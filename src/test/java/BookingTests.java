import Entities.Booking;
import Entities.BookingDates;
import com.github.javafaker.Faker;
import io.restassured.RestAssured;
import io.restassured.filter.log.ErrorLoggingFilter;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.lessThan;

public class BookingTests {

    private static final String BASE_URI = "https://restful-booker.herokuapp.com";
    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASSWORD = "password123";

    private static Faker faker;

    @BeforeAll
    public static void setup() {
        RestAssured.baseURI = BASE_URI;
        faker = new Faker();
        RestAssured.filters(new io.qameta.allure.restassured.AllureRestAssured(),
                new RequestLoggingFilter(),
                new ResponseLoggingFilter(),
                new ErrorLoggingFilter());
    }

    // ---------- Helpers: cada chamada devolve um objeto NOVO (sem estado compartilhado) ----------

    private static RequestSpecification publicRequest() {
        return given().contentType(ContentType.JSON);
    }

    // preemptive() envia o login já na primeira chamada.
    // O Restful-booker responde 403 (não 401) quando falta autenticação,
    // então o basic() "normal" nunca chegaria a enviar as credenciais.
    private static RequestSpecification adminRequest() {
        return publicRequest().auth().preemptive().basic(ADMIN_USER, ADMIN_PASSWORD);
    }

    private static Booking newBooking(String firstName, String lastName) {
        BookingDates dates = new BookingDates("2018-01-02", "2018-01-03");
        return new Booking(firstName, lastName,
                (float) faker.number().numberBetween(50, 100000), // inteiro: o schema de Response exige totalprice integer
                true, dates, "Breakfast");
    }

    private static Booking newBooking() {
        return newBooking(faker.name().firstName(), faker.name().lastName());
    }

    // Cria uma reserva e devolve o id, para os testes não dependerem de dados pré-existentes
    private static int createBookingAndGetId(Booking booking) {
        return publicRequest()
                .body(booking)
                .when().post("/booking")
                .then().statusCode(200)
                .extract().path("bookingid");
    }

    // ---------- GET ----------

    @Test
    @DisplayName("Listar reservas retorna 200 e ao menos uma reserva")
    public void getAllBookings_returnOk() {
        publicRequest()
                .when().get("/booking")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", greaterThan(0));
    }

    @Test
    @DisplayName("Filtrar por firstname encontra a reserva recém-criada")
    public void getBookingsByFirstName_bookingExists_returnOk() {
        String firstName = faker.name().firstName();
        int id = createBookingAndGetId(newBooking(firstName, faker.name().lastName()));

        publicRequest()
                .queryParam("firstname", firstName) // minúsculo, como na documentação da API
                .when().get("/booking")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("bookingid", hasItem(id));
    }

    @Test
    @DisplayName("Buscar reserva por id devolve os dados criados")
    public void getBookingById_returnsCreatedBooking() {
        String firstName = faker.name().firstName();
        int id = createBookingAndGetId(newBooking(firstName, faker.name().lastName()));

        publicRequest()
                .when().get("/booking/{id}", id)
                .then()
                .statusCode(200)
                .body("firstname", equalTo(firstName));
    }

    @Test
    @DisplayName("Buscar id inexistente retorna 404")
    public void getBookingById_nonExistentId_returnNotFound() {
        publicRequest()
                .when().get("/booking/{id}", 999999999)
                .then()
                .statusCode(404);
    }

    // ---------- POST ----------

    @Test
    @DisplayName("Criar reserva com dados válidos retorna 200, schema correto e resposta rápida")
    public void createBooking_withValidData_returnOk() {
        publicRequest()
                .body(newBooking())
                .when().post("/booking")
                .then()
                .assertThat()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(matchesJsonSchemaInClasspath("createBookingResponseSchema.json"))
                .time(lessThan(2000L));
    }

    // ---------- PUT ----------

    @Test
    @DisplayName("Atualizar reserva com autenticação retorna 200 e dados novos")
    public void updateBooking_withAuth_returnOk() {
        int id = createBookingAndGetId(newBooking());
        String newName = faker.name().firstName();

        adminRequest()
                .body(newBooking(newName, faker.name().lastName()))
                .when().put("/booking/{id}", id)
                .then()
                .statusCode(200)
                .body("firstname", equalTo(newName));
    }

    @Test
    @DisplayName("Atualizar reserva sem autenticação retorna 403")
    public void updateBooking_withoutAuth_returnForbidden() {
        int id = createBookingAndGetId(newBooking());

        publicRequest()
                .body(newBooking())
                .when().put("/booking/{id}", id)
                .then()
                .statusCode(403);
    }

    // ---------- DELETE ----------

    @Test
    @DisplayName("Deletar reserva com autenticação retorna 201 e a reserva some")
    public void deleteBooking_withAuth_returnCreated() {
        int id = createBookingAndGetId(newBooking());

        adminRequest()
                .when().delete("/booking/{id}", id)
                .then()
                .statusCode(201); // o Restful-booker responde 201 no delete

        publicRequest()
                .when().get("/booking/{id}", id)
                .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("Deletar reserva sem autenticação retorna 403")
    public void deleteBooking_withoutAuth_returnForbidden() {
        int id = createBookingAndGetId(newBooking());

        publicRequest()
                .when().delete("/booking/{id}", id)
                .then()
                .statusCode(403);
    }
}