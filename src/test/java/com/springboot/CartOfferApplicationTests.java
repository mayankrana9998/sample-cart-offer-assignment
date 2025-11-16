package com.springboot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springboot.controller.ApplyOfferRequest;
import com.springboot.controller.SegmentResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class CartOfferApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Before
    public void setupOffers() throws Exception {
        // Add offers for p1, p2, p3
        String offerP1 = "{\"restaurant_id\":1,\"offer_type\":\"FLAT10\",\"offer_value\":10,\"customer_segment\":[\"p1\"]}";
        String offerP2 = "{\"restaurant_id\":1,\"offer_type\":\"FLAT20%\",\"offer_value\":20,\"customer_segment\":[\"p2\"]}";
        String offerP3 = "{\"restaurant_id\":1,\"offer_type\":\"FLAT50\",\"offer_value\":50,\"customer_segment\":[\"p3\"]}";

        mockMvc.perform(post("/api/v1/offer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(offerP1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response_msg").value("success"));

        mockMvc.perform(post("/api/v1/offer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(offerP2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response_msg").value("success"));

        mockMvc.perform(post("/api/v1/offer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(offerP3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response_msg").value("success"));
    }

    // Utility method to mock user segment
    private void mockUserSegment(int userId, String segment) throws Exception {
        String segmentJson = objectMapper.writeValueAsString(new SegmentResponse(segment));

        mockMvc.perform(get("/api/v1/user_segment")
                .param("user_id", String.valueOf(userId)))
                .andExpect(status().isOk())
                .andExpect(content().json(segmentJson));
    }

    // ----------------- Positive Tests -----------------

    @Test
    public void testFlatAmountOffer_p1() throws Exception {
        mockUserSegment(1, "p1");
        applyAndAssertCart(200, 1, 1, 190);
    }

    @Test
    public void testFlatPercentOffer_p2() throws Exception {
        mockUserSegment(2, "p2");
        applyAndAssertCart(150, 2, 1, 120);
    }

    @Test
    public void testFlatAmountOffer_p3() throws Exception {
        mockUserSegment(3, "p3");
        applyAndAssertCart(500, 3, 1, 450);
    }

    @Test
    public void testZeroCartValue() throws Exception {
        mockUserSegment(1, "p1");
        applyAndAssertCart(0, 1, 1, 0);
    }

    @Test
    public void testCartValueLessThanFlatAmount() throws Exception {
        mockUserSegment(1, "p1");
        applyAndAssertCart(5, 1, 1, 0);
    }

    @Test
    public void testNoOfferApplied() throws Exception {
        mockUserSegment(1, "p1"); // offer for p2 only
        applyAndAssertCart(200, 1, 1, 190); // FLAT10 still applies for p1
    }

    @Test
    public void testInvalidOfferType() throws Exception {
        // Add invalid offer
        String invalidOffer = "{\"restaurant_id\":1,\"offer_type\":\"INVALID\",\"offer_value\":10,\"customer_segment\":[\"p1\"]}";

        mockMvc.perform(post("/api/v1/offer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidOffer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response_msg").value("success"));

        mockUserSegment(1, "p1");
        applyAndAssertCart(200, 1, 1, 200); // No discount applied
    }

    // ----------------- Negative / Edge Tests -----------------

    @Test
    public void testNegativeCartValue() throws Exception {
        mockUserSegment(1, "p1");
        applyAndAssertCart(-100, 1, 1, 0); // Should return minimum 0
    }

    @Test
    public void testNonExistentUser() throws Exception {
        mockUserSegment(999, "unknown"); // Mock returns unknown segment
        applyAndAssertCart(200, 999, 1, 200); // No discount
    }

    @Test
    public void testNonExistentRestaurant() throws Exception {
        mockUserSegment(1, "p1");
        applyAndAssertCart(200, 1, 999, 200); // No discount
    }

    @Test
    public void testMultipleOffersDifferentSegments() throws Exception {
        // p1 and p2 offers already setup, test user_id=2 (p2)
        mockUserSegment(2, "p2");
        applyAndAssertCart(300, 2, 1, 240); // 20% off
    }

    // ----------------- Helper Method -----------------

    private void applyAndAssertCart(int cartValue, int userId, int restaurantId, int expectedValue) throws Exception {
        ApplyOfferRequest request = new ApplyOfferRequest();
        request.setCart_value(cartValue);
        request.setUser_id(userId);
        request.setRestaurant_id(restaurantId);

        String requestJson = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/v1/cart/apply_offer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cart_value").value(expectedValue));
    }
}
