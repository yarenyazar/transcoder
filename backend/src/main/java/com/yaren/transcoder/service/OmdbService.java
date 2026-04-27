package com.yaren.transcoder.service;

import com.yaren.transcoder.dto.OmdbResponse;

public interface OmdbService {
    /**
     * Fetches movie or series details from OMDb API based on their IMDb ID.
     * 
     * @param imdbId Valid IMDb ID (e.g. tt1234567)
     * @return OmdbResponse DTO
     */
    OmdbResponse getDetailsByImdbId(String imdbId);

    /**
     * Searches for movies or series from OMDb API based on title.
     * 
     * @param title Title to search for
     * @return OmdbSearchResponse DTO containing list of results
     */
    com.yaren.transcoder.dto.OmdbSearchResponse searchByTitle(String title);
}
