package net.mysterria.backend.integration;

import net.mysterria.backend.entity.Transaction;
import net.mysterria.backend.entity.enums.TransactionType;
import net.mysterria.backend.repository.TransactionRepository;
import net.mysterria.backend.util.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EconomyControllerIntegrationTest extends BaseHttpIntegrationTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void getBalance_Authenticated_ReturnsBalance() throws Exception {
        mockMvc.perform(get("/api/user/balance")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(testUser.getId().toString()))
                .andExpect(jsonPath("$.balance").value(testUser.getBalance().doubleValue()))
                .andExpect(jsonPath("$.lastUpdated").exists());
    }

    @Test
    void getBalance_NotAuthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/user/balance"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getTransactions_Authenticated_ReturnsPaginatedTransactions() throws Exception {
        // Create test transactions
        Transaction tx1 = TestDataFactory.createTransaction(testUser, new BigDecimal("50.00"), TransactionType.REWARD);
        Transaction tx2 = TestDataFactory.createTransaction(testUser, new BigDecimal("-25.00"), TransactionType.PURCHASE);
        transactionRepository.save(tx1);
        transactionRepository.save(tx2);

        mockMvc.perform(get("/api/user/transactions")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void getTransactions_WithTypeFilter_ReturnsFilteredTransactions() throws Exception {
        Transaction reward = TestDataFactory.createTransaction(testUser, new BigDecimal("50.00"), TransactionType.REWARD);
        Transaction purchase = TestDataFactory.createTransaction(testUser, new BigDecimal("-25.00"), TransactionType.PURCHASE);
        transactionRepository.save(reward);
        transactionRepository.save(purchase);

        mockMvc.perform(get("/api/user/transactions")
                        .param("type", "REWARD")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].type").value("REWARD"));
    }

    @Test
    void getTransactions_WithPagination_ReturnsPagedResults() throws Exception {
        // Create multiple transactions
        for (int i = 0; i < 15; i++) {
            Transaction tx = TestDataFactory.createTransaction(testUser, new BigDecimal("10.00"), TransactionType.REWARD);
            transactionRepository.save(tx);
        }

        mockMvc.perform(get("/api/user/transactions")
                        .param("page", "0")
                        .param("size", "10")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(10))
                .andExpect(jsonPath("$.totalElements").value(15))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void getTransactions_NotAuthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/user/transactions"))
                .andExpect(status().isUnauthorized());
    }
}
