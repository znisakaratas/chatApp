package com.example.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
@RestController
@RequiredArgsConstructor
@RequestMapping("api/users")
public class UsersController {

    private final AppUserRepository repo;

    @GetMapping
    public List<AppUser> tumKullanicilar() {
        return repo.findAll(Sort.by("id"));
    }
}

