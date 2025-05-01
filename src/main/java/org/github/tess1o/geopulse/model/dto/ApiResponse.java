package org.github.tess1o.geopulse.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Standard API response format.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse {
    private String status;
    private String message;
    private Object data;
    
    /**
     * Create a success response with data.
     */
    public static ApiResponse success(Object data) {
        return new ApiResponse("success", null, data);
    }
    
    /**
     * Create a success response with a message.
     */
    public static ApiResponse success(String message) {
        return new ApiResponse("success", message, null);
    }
    
    /**
     * Create a success response with a message and data.
     */
    public static ApiResponse success(String message, Object data) {
        return new ApiResponse("success", message, data);
    }
    
    /**
     * Create an error response with a message.
     */
    public static ApiResponse error(String message) {
        return new ApiResponse("error", message, null);
    }
}