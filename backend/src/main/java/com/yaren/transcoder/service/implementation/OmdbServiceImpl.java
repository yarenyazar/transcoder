package com.yaren.transcoder.service.implementation;

import com.yaren.transcoder.dto.OmdbResponse;
import com.yaren.transcoder.service.OmdbService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class OmdbServiceImpl implements OmdbService {

    // Using a sample API key for dev purposes. Recommend moving to
    // application.properties.
    @Value("${omdb.api.key:thewdb}")
    private String apiKey;

    @Value("${omdb.api.url:http://www.omdbapi.com/}")
    private String apiUrl;

    private final RestTemplate restTemplate;

    public OmdbServiceImpl() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public OmdbResponse getDetailsByImdbId(String imdbId) {
        String urlTemplate = UriComponentsBuilder.fromHttpUrl(apiUrl)
                .queryParam("i", imdbId)
                .queryParam("apikey", apiKey)
                .encode()
                .toUriString();

        return restTemplate.getForObject(urlTemplate, OmdbResponse.class);
    }

    @Override
    public com.yaren.transcoder.dto.OmdbSearchResponse searchByTitle(String title) {
        String urlTemplate = UriComponentsBuilder.fromHttpUrl(apiUrl)
                .queryParam("s", title)
                .queryParam("apikey", apiKey)
                .encode()
                .toUriString();

        return restTemplate.getForObject(urlTemplate, com.yaren.transcoder.dto.OmdbSearchResponse.class);
    }
}
