package com.repairmatch.repairmatch_backend.config;
import com.repairmatch.repairmatch_backend.model.ApplianceType;
import com.repairmatch.repairmatch_backend.repository.ApplianceTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Reference data only: never creates accounts, requests, proposals, or services. */
@Component @RequiredArgsConstructor
public class CatalogBootstrap implements ApplicationRunner {
    private final ApplianceTypeRepository repository;
    @Override @Transactional public void run(ApplicationArguments args) {
        if (repository.count() != 0) return;
        for (String name : new String[]{"Refrigerator", "Washing machine", "Microwave", "Oven", "Air conditioner"})
            repository.save(ApplianceType.builder().name(name).description(name + " repair").build());
    }
}
