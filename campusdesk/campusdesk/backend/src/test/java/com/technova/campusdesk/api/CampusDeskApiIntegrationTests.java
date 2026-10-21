package com.technova.campusdesk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * End-to-end API tests: the real security chain, JWT, services and JPA (H2 in memory), driven through MockMvc.
 * They cover the mandatory test cases that can be automated (CP-01 to CP-13 and CP-16).
 * MockMvc is built by hand with the Spring Security configurer, which does not depend on Spring Boot test slices.
 */
@SpringBootTest
@ActiveProfiles("test")
class CampusDeskApiIntegrationTests {

    private static final String ADMIN_EMAIL = "admin@test.local";          // application-test.properties
    private static final String ADMIN_PASSWORD = "Test-Admin-123!";
    private static final String PASSWORD = "Strong-Pass-123!";
    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
    private static final Pattern TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    // ---------------------------------------------------------------- authentication (CP-03, CP-04, CP-16)

    @Test
    void loginWithValidCredentialsReturnsAJwt() throws Exception {
        MvcResult result = mvc.perform(json(post("/api/auth/login"),
                login(ADMIN_EMAIL, ADMIN_PASSWORD))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).containsPattern(TOKEN);
    }

    @Test
    void loginWithAWrongPasswordIsRejected() throws Exception {
        MvcResult result = mvc.perform(json(post("/api/auth/login"),
                login(ADMIN_EMAIL, "Wrong-Password-1!"))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void protectedEndpointWithoutTokenIsUnauthorized() throws Exception {
        assertThat(status(get("/api/tickets"))).isEqualTo(401);
    }

    @Test
    void invalidOrTamperedTokenIsUnauthorized() throws Exception {
        assertThat(status(get("/api/tickets").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.valid.token")))
                .isEqualTo(401);

        String valid = tokenFor(ADMIN_EMAIL, ADMIN_PASSWORD);
        String tampered = valid.substring(0, valid.length() - 3) + (valid.endsWith("abc") ? "xyz" : "abc");
        assertThat(status(get("/api/tickets").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered)))
                .isEqualTo(401);
    }

    // ---------------------------------------------------------------- user provisioning (CP-01, CP-02)

    @Test
    void thereIsNoPublicRegistration() throws Exception {
        String body = """
                {"fullName":"Intruder","email":"%s","password":"%s"}""".formatted(uniqueEmail("intruder"), PASSWORD);

        assertThat(status(json(post("/api/auth/register"), body))).isEqualTo(401);
    }

    @Test
    void aRegularUserCannotRegisterAccounts() throws Exception {
        String userToken = newUserToken("USER");
        String body = """
                {"fullName":"Other","email":"%s","password":"%s"}""".formatted(uniqueEmail("other"), PASSWORD);

        assertThat(status(auth(json(post("/api/auth/register"), body), userToken))).isEqualTo(403);
    }

    @Test
    void anAdministratorRegistersAUserAndDuplicatesAreRejected() throws Exception {
        String admin = tokenFor(ADMIN_EMAIL, ADMIN_PASSWORD);
        String email = uniqueEmail("laura");
        String body = """
                {"fullName":"Laura Gomez","email":"%s","password":"%s"}""".formatted(email, PASSWORD);

        MvcResult created = mvc.perform(auth(json(post("/api/auth/register"), body), admin)).andReturn();
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        assertThat(created.getResponse().getContentAsString()).contains("USER");

        assertThat(status(auth(json(post("/api/auth/register"), body), admin))).isEqualTo(409);
    }

    // ---------------------------------------------------------------- tickets (CP-05, CP-06, CP-08)

    @Test
    void aNewTicketStartsOpen() throws Exception {
        String user = newUserToken("USER");

        MvcResult result = mvc.perform(auth(json(post("/api/tickets"), ticketBody()), user)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(result.getResponse().getContentAsString()).contains("\"status\":\"OPEN\"");
    }

    @Test
    void aUserCannotReadSomeoneElsesTicket() throws Exception {
        long ticketId = createTicket(newUserToken("USER"));
        String intruder = newUserToken("USER");

        int status = status(auth(get("/api/tickets/" + ticketId), intruder));

        assertThat(status).isIn(403, 404);
    }

    @Test
    void aUserCannotAssignTechnicians() throws Exception {
        String owner = newUserToken("USER");
        long ticketId = createTicket(owner);

        int status = status(auth(json(patch("/api/tickets/" + ticketId + "/assign"),
                "{\"technicianId\":1}"), owner));

        assertThat(status).isEqualTo(403);
    }

    @Test
    void searchReturnsAPageEnvelopeLimitedToTheUsersTickets() throws Exception {
        String user = newUserToken("USER");
        createTicket(user);
        createTicket(user);

        MvcResult result = mvc.perform(auth(get("/api/tickets/search?page=0&size=1&sort=createdAt&direction=desc"), user))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body).contains("\"totalElements\":2").contains("\"totalPages\":2");
    }

    @Test
    void searchRejectsAnUnknownSortField() throws Exception {
        String user = newUserToken("USER");

        assertThat(status(auth(get("/api/tickets/search?sort=passwordHash"), user))).isEqualTo(400);
    }

    // ---------------------------------------------------------------- full life cycle (CP-07, CP-10 to CP-13)

    @Test
    void fullLifeCycleFromCreationToClosing() throws Exception {
        String admin = tokenFor(ADMIN_EMAIL, ADMIN_PASSWORD);
        String owner = newUserToken("USER");
        String technicianEmail = uniqueEmail("tech");
        long technicianId = createUser(admin, "Carlos Tech", technicianEmail, "TECHNICIAN");
        String technician = tokenFor(technicianEmail, PASSWORD);
        long ticketId = createTicket(owner);

        // The technician cannot jump over the flow: an unassigned ticket cannot be resolved (409)
        assertThat(status(auth(json(patch("/api/tickets/" + ticketId + "/status"),
                "{\"status\":\"RESOLVED\"}"), technician))).isIn(403, 409);

        // CP-07: the administrator assigns
        MvcResult assigned = mvc.perform(auth(json(patch("/api/tickets/" + ticketId + "/assign"),
                "{\"technicianId\":" + technicianId + "}"), admin)).andReturn();
        assertThat(assigned.getResponse().getStatus()).isEqualTo(200);
        assertThat(assigned.getResponse().getContentAsString()).contains("\"status\":\"ASSIGNED\"");

        // CP-10: RESOLVED without passing through IN_PROGRESS is a business-rule conflict
        assertThat(status(auth(json(patch("/api/tickets/" + ticketId + "/status"),
                "{\"status\":\"RESOLVED\"}"), technician))).isEqualTo(409);

        // The technician works and resolves; the requester closes (CP-12)
        assertThat(status(auth(json(patch("/api/tickets/" + ticketId + "/status"),
                "{\"status\":\"IN_PROGRESS\"}"), technician))).isEqualTo(200);
        assertThat(status(auth(json(patch("/api/tickets/" + ticketId + "/status"),
                "{\"status\":\"RESOLVED\",\"note\":\"Replaced the cable\"}"), technician))).isEqualTo(200);
        MvcResult closed = mvc.perform(auth(json(patch("/api/tickets/" + ticketId + "/status"),
                "{\"status\":\"CLOSED\"}"), owner)).andReturn();
        assertThat(closed.getResponse().getStatus()).isEqualTo(200);
        assertThat(closed.getResponse().getContentAsString()).contains("\"status\":\"CLOSED\"");

        // CP-11: every change left a history entry (created, assigned, in progress, resolved, closed)
        String history = mvc.perform(auth(get("/api/tickets/" + ticketId + "/history"), owner))
                .andReturn().getResponse().getContentAsString();
        assertThat(history).contains("ASSIGNED", "IN_PROGRESS", "RESOLVED", "CLOSED");

        // CP-13: a closed ticket rejects comments
        assertThat(status(auth(json(post("/api/tickets/" + ticketId + "/comments"),
                "{\"content\":\"One more thing\"}"), owner))).isEqualTo(409);
    }

    @Test
    void theRequesterCanCancelAnOpenTicketButNotAStranger() throws Exception {
        String owner = newUserToken("USER");
        String stranger = newUserToken("USER");
        long ticketId = createTicket(owner);

        assertThat(status(auth(json(patch("/api/tickets/" + ticketId + "/status"),
                "{\"status\":\"CANCELLED\"}"), stranger))).isIn(403, 404);
        assertThat(status(auth(json(patch("/api/tickets/" + ticketId + "/status"),
                "{\"status\":\"CANCELLED\"}"), owner))).isEqualTo(200);
    }

    // ---------------------------------------------------------------- helpers

    private String ticketBody() {
        return """
                {"title":"Printer is jammed","description":"The printer on floor 2 jams on every job.",
                 "category":"HARDWARE","priority":"MEDIUM"}""";
    }

    private long createTicket(String token) throws Exception {
        MvcResult result = mvc.perform(auth(json(post("/api/tickets"), ticketBody()), token)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return firstId(result.getResponse().getContentAsString());
    }

    /** Creates an account through the administrator endpoint and returns its id. */
    private long createUser(String adminToken, String fullName, String email, String role) throws Exception {
        String body = """
                {"fullName":"%s","email":"%s","password":"%s","role":"%s"}""".formatted(fullName, email, PASSWORD, role);
        MvcResult result = mvc.perform(auth(json(post("/api/users"), body), adminToken)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return firstId(result.getResponse().getContentAsString());
    }

    /** A brand-new account with the given role and its token. */
    private String newUserToken(String role) throws Exception {
        String email = uniqueEmail(role.toLowerCase());
        createUser(tokenFor(ADMIN_EMAIL, ADMIN_PASSWORD), "Test " + role, email, role);
        return tokenFor(email, PASSWORD);
    }

    private String tokenFor(String email, String password) throws Exception {
        MvcResult result = mvc.perform(json(post("/api/auth/login"), login(email, password))).andReturn();
        assertThat(result.getResponse().getStatus()).as("login of %s", email).isEqualTo(200);
        Matcher matcher = TOKEN.matcher(result.getResponse().getContentAsString());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static String login(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private static long firstId(String json) {
        Matcher matcher = ID.matcher(json);
        assertThat(matcher.find()).as("id in %s", json).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private int status(MockHttpServletRequestBuilder builder) throws Exception {
        return mvc.perform(builder).andReturn().getResponse().getStatus();
    }
}
