package com.product.realtime.controller;

import com.product.pojo.other.MyUserDetails;
import com.product.pojo.vo.Result;
import com.product.realtime.websocket.RealtimeTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/realtime")
public class RealtimeController {
    private final RealtimeTicketService ticketService;

    @PostMapping("/tickets")
    public Result<TicketResponse> createTicket(
            @AuthenticationPrincipal MyUserDetails userDetails) {
        RealtimeTicketService.IssuedTicket issued = ticketService.issue(
                userDetails.getUser().getUserId()
        );
        return Result.success(new TicketResponse(
                issued.ticket(), issued.expiresAt()
        ));
    }

    public record TicketResponse(String ticket, Instant expiresAt) { }
}
