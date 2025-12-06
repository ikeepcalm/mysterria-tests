package net.mysterria.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.mysterria.backend.BaseIntegrationTest;
import net.mysterria.backend.entity.Role;
import net.mysterria.backend.entity.User;
import net.mysterria.backend.repository.RoleRepository;
import net.mysterria.backend.repository.UserRepository;
import net.mysterria.backend.service.JwtService;
import net.mysterria.backend.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

@AutoConfigureMockMvc
public abstract class BaseHttpIntegrationTest extends BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected RoleRepository roleRepository;

    @Autowired
    protected JwtService jwtService;

    protected User testUser;
    protected Role memberRole;
    protected String accessToken;

    @BeforeEach
    void setUpTestUser() {
        // Create member role
        memberRole = TestDataFactory.createMemberRole();
        memberRole = roleRepository.save(memberRole);

        // Create test user
        testUser = TestDataFactory.createTestUser(123456789L, memberRole);
        testUser = userRepository.save(testUser);

        // Generate access token
        accessToken = jwtService.generateToken(testUser.getId(), Map.of(
                "role", memberRole.getName(),
                "permissions", memberRole.getPermissions(),
                "username", testUser.getNickname()
        ));
    }
}
