package net.mysterria.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.mysterria.backend.config.TestSecurityConfig;
import net.mysterria.backend.dto.economy.BalanceDto;
import net.mysterria.backend.dto.economy.TransactionDto;
import net.mysterria.backend.dto.economy.TransactionHistoryRequest;
import net.mysterria.backend.entity.enums.TransactionType;
import net.mysterria.backend.security.JwtAuthenticationFilter;
import net.mysterria.backend.service.EconomyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = EconomyController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
)
@Import(TestSecurityConfig.class)
class EconomyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EconomyService economyService;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID userId;
    private BalanceDto balanceDto;
    private TransactionDto transactionDto;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        balanceDto = BalanceDto.builder()
                .userId(userId)
                .balance(new BigDecimal("1000.00"))
                .lastUpdated(LocalDateTime.now())
                .build();

        transactionDto = TransactionDto.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .amount(new BigDecimal("100.00"))
                .type(TransactionType.REWARD)
                .description("Test transaction")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void getBalance_NotAuthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/user/balance"))
                .andExpect(status().isUnauthorized());

        verify(economyService, never()).getBalance(any(UUID.class));
    }

    @Test
    void getTransactions_NotAuthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/user/transactions"))
                .andExpect(status().isUnauthorized());

        verify(economyService, never()).getTransactionHistory(any(UUID.class), any(TransactionHistoryRequest.class), any(Pageable.class));
    }
}