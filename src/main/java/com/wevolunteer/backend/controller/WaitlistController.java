package com.wevolunteer.backend.controller;

import com.wevolunteer.backend.model.Waitlist;
import com.wevolunteer.backend.service.WaitlistService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class WaitlistController {

    private final WaitlistService waitlistService;

    public WaitlistController(WaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }

    @GetMapping("/waitlist/me")
    public List<Waitlist> getMyWaitlist(@AuthenticationPrincipal Jwt jwt) {
        return waitlistService.getWaitlistByUserId(jwt.getSubject());
    }

    @PostMapping("/waitlist/me/{opportunityId}")
    public void joinMyWaitlist(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String opportunityId) {

        waitlistService.join(jwt.getSubject(), opportunityId);
    }

    @DeleteMapping("/waitlist/me/{opportunityId}")
    public void leaveMyWaitlist(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String opportunityId) {

        waitlistService.leave(jwt.getSubject(), opportunityId);
    }
}
