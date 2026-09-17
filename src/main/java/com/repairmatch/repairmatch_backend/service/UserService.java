package com.repairmatch.repairmatch_backend.service;

import com.repairmatch.repairmatch_backend.dto.RegisterRequestDto;
import com.repairmatch.repairmatch_backend.dto.UserResponseDto;
import com.repairmatch.repairmatch_backend.model.User;
import com.repairmatch.repairmatch_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.repairmatch.repairmatch_backend.exception.EmailAlreadyExistsException;
import com.repairmatch.repairmatch_backend.dto.LoginRequestDto;
import com.repairmatch.repairmatch_backend.dto.LoginResponseDto;


import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;


    @Transactional
    public UserResponseDto register(RegisterRequestDto request) {

        String email = request.getEmail()
                .strip()
                .toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException("El correo ya está registrado");
        }

        if (request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La contraseña no puede superar 72 bytes en UTF-8"
            );
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());

        User user = new User(
                request.getName().strip(),
                email,
                passwordHash,
                request.getRole()
        );

        User savedUser = userRepository.saveAndFlush(user);

        return new UserResponseDto(
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getRole()
        );
    }

    @Transactional(readOnly = true)
    public LoginResponseDto login(LoginRequestDto request) {
        String email = request.getEmail()
                .strip()
                .toLowerCase(Locale.ROOT);

        if (request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Credenciales inválidas"
            );
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Credenciales inválidas"
                ));

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPasswordHash()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Credenciales inválidas"
            );
        }

        return jwtService.generateToken(user);
    }
    @Transactional(readOnly = true)
    public UserResponseDto getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "La cuenta autenticada ya no está disponible"
                ));

        return new UserResponseDto(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }
}