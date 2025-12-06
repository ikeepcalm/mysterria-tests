package net.mysterria.backend.integration;

import net.mysterria.backend.dto.shop.PurchaseRequest;
import net.mysterria.backend.entity.Purchase;
import net.mysterria.backend.entity.ServiceCategory;
import net.mysterria.backend.repository.PurchaseRepository;
import net.mysterria.backend.repository.ServiceCategoryRepository;
import net.mysterria.backend.repository.ServiceRepository;
import net.mysterria.backend.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShopControllerIntegrationTest extends BaseHttpIntegrationTest {

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private ServiceCategoryRepository serviceCategoryRepository;

    @Autowired
    private PurchaseRepository purchaseRepository;

    private ServiceCategory vipCategory;
    private net.mysterria.backend.entity.Service cheapService;
    private net.mysterria.backend.entity.Service expensiveService;

    @BeforeEach
    void setUpServices() {
        vipCategory = TestDataFactory.createServiceCategory("VIP");
        vipCategory = serviceCategoryRepository.save(vipCategory);

        cheapService = TestDataFactory.createService(vipCategory, new BigDecimal("50.00"));
        cheapService.setName("Basic VIP");
        cheapService.setDescription("Basic VIP rank");
        cheapService = serviceRepository.save(cheapService);

        expensiveService = TestDataFactory.createService(vipCategory, new BigDecimal("200.00"));
        expensiveService.setName("Premium VIP");
        expensiveService.setDescription("Premium VIP rank");
        expensiveService = serviceRepository.save(expensiveService);
    }

    @Test
    void getAllServices_ReturnsActiveServices() throws Exception {
        mockMvc.perform(get("/api/shop/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getAffordableServices_Authenticated_ReturnsOnlyAffordable() throws Exception {
        // testUser has 100.00 balance, can only afford cheapService (50.00)
        testUser.setBalance(new BigDecimal("100.00"));
        userRepository.save(testUser);

        mockMvc.perform(get("/api/shop/services/affordable")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Basic VIP"));
    }

    @Test
    void getAffordableServices_NotAuthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/shop/services/affordable"))
                .andExpect(status().isForbidden());
    }

    @Test
    void purchaseService_NotAuthenticated_ReturnsUnauthorized() throws Exception {
        PurchaseRequest request = new PurchaseRequest(cheapService.getId());

        mockMvc.perform(post("/api/shop/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUserPurchases_Authenticated_ReturnsPaginatedPurchases() throws Exception {
        Purchase purchase1 = TestDataFactory.createPurchase(testUser, cheapService);
        Purchase purchase2 = TestDataFactory.createPurchase(testUser, expensiveService);
        purchaseRepository.save(purchase1);
        purchaseRepository.save(purchase2);

        mockMvc.perform(get("/api/shop/purchases")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void getUserPurchases_NotAuthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/shop/purchases"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUserActivePurchases_Authenticated_ReturnsOnlyActive() throws Exception {
        Purchase activePurchase = TestDataFactory.createPurchase(testUser, cheapService);
        activePurchase.setExpiresAt(java.time.LocalDateTime.now().plusDays(10));

        Purchase expiredPurchase = TestDataFactory.createPurchase(testUser, expensiveService);
        expiredPurchase.setExpiresAt(java.time.LocalDateTime.now().minusDays(1));

        purchaseRepository.save(activePurchase);
        purchaseRepository.save(expiredPurchase);

        mockMvc.perform(get("/api/shop/purchases/active")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getUserActivePurchases_NotAuthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/shop/purchases/active"))
                .andExpect(status().isForbidden());
    }
}
