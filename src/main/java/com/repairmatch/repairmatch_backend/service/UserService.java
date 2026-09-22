package com.repairmatch.repairmatch_backend.service;

import com.repairmatch.repairmatch_backend.dto.RegisterRequestDto;
import com.repairmatch.repairmatch_backend.dto.UserResponseDto;
import com.repairmatch.repairmatch_backend.model.User;
import com.repairmatch.repairmatch_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.repairmatch.repairmatch_backend.exception.*;
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
    private final ModelMapper modelMapper;


    @Transactional
    public UserResponseDto register(RegisterRequestDto request) {

        String email = request.getEmail()
                .strip()
                .toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException("El correo ya está registrado");
        }

        if (request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidPasswordException();
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());

        User user = new User(
                request.getName().strip(),
                email,
                passwordHash,
                request.getRole()
        );

        User savedUser = userRepository.saveAndFlush(user);

        return modelMapper.map(savedUser, UserResponseDto.class);
    }

    @Transactional(readOnly = true)
    public LoginResponseDto login(LoginRequestDto request) {
        String email = request.getEmail()
                .strip()
                .toLowerCase(Locale.ROOT);

        if (request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidCredentialsException();
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException());

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPasswordHash()
        )) {
            throw new InvalidCredentialsException();
        }

        return jwtService.generateToken(user);
    }
    @Transactional(readOnly = true)
    public UserResponseDto getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AccountUnavailableException());

        return modelMapper.map(user, UserResponseDto.class);
    }
}