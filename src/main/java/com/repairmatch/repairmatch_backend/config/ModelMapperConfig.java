package com.repairmatch.repairmatch_backend.config;

import com.repairmatch.repairmatch_backend.dto.UserResponseDto;
import com.repairmatch.repairmatch_backend.model.User;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModelMapperConfig {

    @Bean
    public ModelMapper modelMapper() {
        ModelMapper mapper = new ModelMapper();
        mapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);
        mapper.createTypeMap(User.class, UserResponseDto.class);
        mapper.validate();
        return mapper;
    }
}
