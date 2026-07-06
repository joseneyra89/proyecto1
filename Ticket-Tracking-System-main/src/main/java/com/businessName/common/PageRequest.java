package com.businessName.common;

import com.businessName.security.AuthException;
import org.json.JSONArray;
import org.json.JSONObject;

public class PageRequest {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;

    public final int page;
    public final int pageSize;
    public final int offset;

    private PageRequest(int page, int pageSize) {
        this.page = page;
        this.pageSize = pageSize;
        long offsetValue = (long) (page - 1) * pageSize;
        if (offsetValue > Integer.MAX_VALUE) {
            throw new AuthException(400, "page offset is too large");
        }
        this.offset = (int) offsetValue;
    }

    public static PageRequest from(String pageParam, String pageSizeParam) {
        int page = parsePositiveInt(pageParam, DEFAULT_PAGE, "page");
        int pageSize = parsePositiveInt(pageSizeParam, DEFAULT_PAGE_SIZE, "pageSize");
        if (pageSize > MAX_PAGE_SIZE) {
            throw new AuthException(400, "pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
        return new PageRequest(page, pageSize);
    }

    public JSONObject toResponse(JSONArray data, long total) {
        long totalPages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        return new JSONObject()
                .put("data", data)
                .put("pagination", new JSONObject()
                        .put("page", page)
                        .put("pageSize", pageSize)
                        .put("total", total)
                        .put("totalPages", totalPages)
                        .put("hasNext", totalPages > page)
                        .put("hasPrevious", page > 1 && totalPages > 0));
    }

    public JSONObject toResponseFromFullList(JSONArray allItems) {
        JSONArray pageItems = new JSONArray();
        int start = Math.min(offset, allItems.length());
        int end = Math.min(start + pageSize, allItems.length());
        for (int i = start; i < end; i++) {
            pageItems.put(allItems.get(i));
        }
        return toResponse(pageItems, allItems.length());
    }

    private static int parsePositiveInt(String value, int defaultValue, String field) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < 1) {
                throw new NumberFormatException(field + " must be positive");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new AuthException(400, field + " must be a positive integer");
        }
    }
}
