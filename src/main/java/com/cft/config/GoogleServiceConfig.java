package com.cft.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.services.AbstractGoogleClient;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;

@Configuration
@PropertySource("classpath:cft.properties")
public class GoogleServiceConfig {

    public static final String SNAPSHOT_DRIVE_FOLDER_ID = "1r7VrrQ4hYPPZoRm0KeDEYekorh8PqGGu";
    public static final String GOOGLE_SHEETS_MIME_TYPE = "application/vnd.google-apps.spreadsheet";
    public static final String GOOGLE_SHEETS_INPUT_OPTION = "RAW";

    private static final String GOOGLE_CREDENTIAL_PATH_PROPERTY = "cft.google.credential-path";

    @Autowired
    private Environment env;

    private final Logger logger = LoggerFactory.getLogger(GoogleServiceConfig.class);

    private <T extends AbstractGoogleClient.Builder> T getGoogleServiceBuilder(GoogleServiceBuilderSupplier<T> builderSupplier) {
        HttpTransport httpTransport = this.getGoogleHttpTransport();
        GoogleCredentials credentials = this.getGoogleCredentials();

        if(httpTransport == null || credentials == null)
            return null;

        return builderSupplier.get(httpTransport, GsonFactory.getDefaultInstance(), new HttpCredentialsAdapter(credentials));
    }

    @Bean
    public HttpTransport getGoogleHttpTransport() {
        try {
            return GoogleNetHttpTransport.newTrustedTransport();
        }
        catch(IOException | GeneralSecurityException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    @Bean
    public GoogleCredentials getGoogleCredentials() {
        String credentialsPath = this.env.getProperty(GOOGLE_CREDENTIAL_PATH_PROPERTY);

        if(credentialsPath == null) {
            this.logger.error("Google service account credential file path is not set.");
            return null;
        }

        ClassPathResource resource = new ClassPathResource(credentialsPath);

        try {
            InputStream credentialStream = resource.getInputStream();
            return GoogleCredentials.fromStream(credentialStream).createScoped(SheetsScopes.SPREADSHEETS, DriveScopes.DRIVE);
        }
        catch(IOException ex) {
            this.logger.error("Error reading google credentials file", ex);
            return null;
        }
    }

    @Bean
    public Sheets getGoogleSheetsService() {
        Sheets.Builder builder = this.getGoogleServiceBuilder(Sheets.Builder::new);

        if(builder == null) {
            this.logger.error("Unable to initialize google sheets service.");
            return null;
        }

        this.logger.info("Initialized google sheets service");
        return builder.setApplicationName("CFT Sheets").build();
    }

    @Bean
    public Drive getGoogleDriveService() {
        Drive.Builder builder = this.getGoogleServiceBuilder(Drive.Builder::new);

        if(builder == null) {
            this.logger.error("Unable to initialize google drive service.");
            return null;
        }

        this.logger.info("Initialized google drive service");
        return builder.setApplicationName("CFT Drive").build();
    }

    @FunctionalInterface
    private interface GoogleServiceBuilderSupplier<T extends AbstractGoogleClient.Builder> {
        T get(HttpTransport transport, JsonFactory factory, HttpRequestInitializer initializer);
    }
}
