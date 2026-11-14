// Seeds presentation data through the public API (never the database directly).
//
//   node scripts/seed-demo.cjs                      -> http://127.0.0.1:8080
//   node scripts/seed-demo.cjs https://api.example  -> a deployed API (asks for --yes)
//
// Accounts use DEMO_PASSWORD (default only for local runs). Running it twice
// reuses the accounts (login instead of register) and adds the requests again.
const assert = require('node:assert/strict');

const base = (process.argv.find((arg) => /^https?:\/\//.test(arg)) || 'http://127.0.0.1:8080').replace(/\/+$/, '');
const local = ['localhost', '127.0.0.1', '[::1]'].includes(new URL(base).hostname);
const password = process.env.DEMO_PASSWORD || (local ? 'DemoRepair2026!' : '');
assert.ok(local || process.argv.includes('--yes'), 'Seeding a non-local API needs --yes');
assert.ok(password.length >= 8, 'Set DEMO_PASSWORD (8+ characters) for a non-local API');

// Lima, around Miraflores / San Isidro / Surco.
const SPOTS = [
  [-12.1211, -77.0297], [-12.0977, -77.0365], [-12.1391, -76.9947],
  [-12.1058, -77.0412], [-12.0889, -77.0505], [-12.1300, -77.0180],
];
const CLIENT = { name: 'Lucía Fernández', email: 'lucia.demo@repairmatch.pe', role: 'CLIENT' };
const TECHS = [
  { name: 'Carlos Quispe', email: 'carlos.demo@repairmatch.pe', role: 'TECHNICIAN', years: 12, radius: 15,
    bio: 'Refrigeración y lavado. Garantía de 90 días en reparaciones.', spot: [-12.1150, -77.0300] },
  { name: 'Rosa Mendoza', email: 'rosa.demo@repairmatch.pe', role: 'TECHNICIAN', years: 7, radius: 20,
    bio: 'Electrodomésticos de cocina y microondas. Atención el mismo día.', spot: [-12.1000, -77.0400] },
  { name: 'Jorge Salazar', email: 'jorge.demo@repairmatch.pe', role: 'TECHNICIAN', years: 4, radius: 10,
    bio: 'Lavadoras y secadoras. Diagnóstico a domicilio.', spot: [-12.1300, -77.0150] },
];
const REQUESTS = [
  ['refri', 'Dejó de enfriar desde ayer; la luz interior sí prende y el motor hace ruido.', 'Samsung', 'RT38K5930SL'],
  ['lava', 'No centrifuga y al final del ciclo la ropa queda empapada.', 'LG', 'WM22VV2S6R'],
  ['micro', 'Calienta poco y a veces salta la llave del tablero al encenderlo.', 'Panasonic', 'NN-ST342W'],
  ['cocina', 'Una hornilla no enciende y huele a gas al girar la perilla.', 'Mabe', 'EM7630BAPS0'],
  ['refri', 'Se forma hielo en la parte de atrás y gotea agua debajo de los cajones.', 'LG', 'GT32BPP'],
  ['lava', 'Hace un golpeteo fuerte al lavar, como si algo estuviera suelto.', 'Whirlpool', 'WFW5620HW'],
  ['aire', 'Prende pero solo bota aire caliente, aunque esté en modo frío.', 'Daikin', 'FTKF35'],
  ['micro', 'El plato no gira y la puerta no cierra bien.', 'Samsung', 'MS23K3513AS'],
  ['refri', 'El congelador funciona pero el refrigerador está tibio.', 'Mabe', 'RMS400IAMRX0'],
  ['cocina', 'El horno no llega a la temperatura y tarda mucho en dorar.', 'Indurama', 'Ambato'],
  ['lava', 'No bota el agua; se queda llena al terminar.', 'Samsung', 'WA13T5260BY'],
  ['aire', 'Gotea agua por dentro de la habitación y hace un zumbido.', 'Midea', 'MSAG-12'],
  ['refri', 'Suena un clic cada pocos minutos y no arranca el compresor.', 'Bosch', 'KGN36NL30'],
  ['micro', 'Hace chispas por dentro al usarlo con cualquier recipiente.', 'LG', 'MS2042D'],
];

async function call(method, path, token, body, expected) {
  const response = await fetch(base + path, {
    method,
    signal: AbortSignal.timeout(20000),
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  const text = await response.text();
  const data = text ? JSON.parse(text) : null;
  if (expected && !expected.includes(response.status)) {
    throw new Error(`${method} ${path} -> ${response.status}: ${text.slice(0, 200)}`);
  }
  return { status: response.status, data };
}

async function account(user) {
  const created = await call('POST', '/api/auth/register', null, { ...user, password }, [201, 409]);
  const login = await call('POST', '/api/auth/login', null, { email: user.email, password }, [200]);
  console.log(`${created.status === 201 ? 'created' : 'reused '} ${user.role.padEnd(10)} ${user.email}`);
  const me = await call('GET', '/api/users/me', login.data.accessToken, null, [200]);
  return { token: login.data.accessToken, id: me.data.id };
}

function typeFor(catalog, hint) {
  const words = { refri: /refrig|fridge/i, lava: /wash/i, micro: /micro/i, cocina: /oven|stove/i, aire: /air/i };
  return (catalog.find((t) => words[hint].test(t.name)) || catalog[0]).id;
}

function inDays(days, hour) {
  const date = new Date(Date.now() + days * 86400000);
  date.setHours(hour, 0, 0, 0);
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(hour)}:00:00`;
}

async function main() {
  console.log('Seeding demo data on ' + base);
  const client = await account(CLIENT);
  const techs = [];
  for (const tech of TECHS) techs.push({ ...tech, ...(await account(tech)) });

  const catalog = (await call('GET', '/api/appliance-types', client.token, null, [200])).data;
  assert.ok(catalog.length > 0, 'The appliance catalog is empty');
  const allTypes = catalog.map((t) => t.id);
  for (const tech of techs) {
    await call('PUT', '/api/technicians/me/profile', tech.token,
      { experienceYears: tech.years, bio: tech.bio, phone: '+51 9' + String(tech.years).padStart(2, '0') + '123456', applianceTypeIds: allTypes }, [200]);
    await call('PUT', '/api/technicians/me/service-areas', tech.token,
      { latitude: tech.spot[0], longitude: tech.spot[1], maxRadiusKm: tech.radius }, [200]);
  }

  const created = [];
  for (const [index, [hint, description, brand, model]] of REQUESTS.entries()) {
    const [latitude, longitude] = SPOTS[index % SPOTS.length];
    const { data } = await call('POST', '/api/requests', client.token,
      { applianceTypeId: typeFor(catalog, hint), originalDescription: description, brand, model, latitude, longitude }, [201]);
    created.push(data);
  }
  console.log(`published ${created.length} requests (more than one page of 10)`);

  // Offers on the first five requests, from one to three technicians each.
  const offers = [[0, 1, 2], [0, 1], [1], [2, 0], [1, 2]];
  const proposals = [];
  for (const [r, techIndexes] of offers.entries()) {
    for (const [k, t] of techIndexes.entries()) {
      const result = await call('POST', `/api/requests/${created[r].id}/proposals`, techs[t].token,
        { diagnosticCost: [35, 45.5, 60, 30, 50][(r + k) % 5], availableAt: inDays(1 + k, 9 + 2 * t),
          conditions: ['Diagnóstico a domicilio; repuestos se cotizan aparte.', 'Incluye revisión completa y limpieza.', 'Visita en el día; garantía de 90 días.'][t] },
        [201, 403, 409]);
      if (result.status === 201) proposals.push({ request: r, tech: t, id: result.data.id });
    }
  }
  console.log(`sent ${proposals.length} offers`);

  // Request #2: accept, attend and complete, then review it.
  const accepted = proposals.find((p) => p.request === 1);
  if (accepted) {
    const service = (await call('POST', `/api/proposals/${accepted.id}/accept`, client.token, null, [201])).data;
    await call('PATCH', `/api/services/${service.id}/status?status=EN_ATENCION`, techs[accepted.tech].token, null, [200]);
    await call('PATCH', `/api/services/${service.id}/status?status=COMPLETADO`, techs[accepted.tech].token, null, [200]);
    await call('POST', `/api/services/${service.id}/review`, client.token,
      { rating: 5, comment: 'Llegó puntual, explicó la falla y dejó la lavadora funcionando.' }, [201]);
    console.log('completed and reviewed one service');
  }
  // Request #4: accepted, still scheduled. Request #14: cancelled.
  const scheduled = proposals.find((p) => p.request === 3);
  if (scheduled) await call('POST', `/api/proposals/${scheduled.id}/accept`, client.token, null, [201]);
  await call('PATCH', `/api/requests/${created[created.length - 1].id}/cancel`, client.token, null, [200]);

  console.log('\nDemo accounts (password from DEMO_PASSWORD):');
  for (const user of [CLIENT, ...TECHS]) console.log(`  ${user.role.padEnd(10)} ${user.email}`);
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
