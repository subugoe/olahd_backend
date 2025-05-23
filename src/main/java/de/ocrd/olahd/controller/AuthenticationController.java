package de.ocrd.olahd.controller;

import de.ocrd.olahd.component.TokenProvider;
import de.ocrd.olahd.domain.ResponseMessage;
import de.ocrd.olahd.domain.TokenResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Api(description = "This endpoint is used to get an access token.")
@RestController
public class AuthenticationController {

    private final TokenProvider tokenProvider;

    private final AuthenticationManager authenticationManager;

    public AuthenticationController(TokenProvider tokenProvider, AuthenticationManager authenticationManager) {
        this.tokenProvider = tokenProvider;
        this.authenticationManager = authenticationManager;
    }

    @ApiOperation(value = "Submit a valid username and password to get back an access token.", httpMethod = "POST")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Login successfully", response = TokenResponse.class),
            @ApiResponse(code = 401, message = "Invalid credentials.", response = ResponseMessage.class)
    })
    @PostMapping(value = "/login", consumes = MediaType.MULTIPART_FORM_DATA_VALUE ,produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TokenResponse> login(@RequestParam String username, @RequestParam String password) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
        );
        String token = tokenProvider.createToken(authentication);
        return ResponseEntity.ok(new TokenResponse(token));
    }
}
