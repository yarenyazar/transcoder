package com.yaren.transcoder.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class OmdbSearchResponse {

    @JsonProperty("Search")
    private List<OmdbSearchResult> search;

    @JsonProperty("totalResults")
    private String totalResults;

    @JsonProperty("Response")
    private String response;

    @JsonProperty("Error")
    private String error;

    public List<OmdbSearchResult> getSearch() {
        return search;
    }

    public void setSearch(List<OmdbSearchResult> search) {
        this.search = search;
    }

    public String getTotalResults() {
        return totalResults;
    }

    public void setTotalResults(String totalResults) {
        this.totalResults = totalResults;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
