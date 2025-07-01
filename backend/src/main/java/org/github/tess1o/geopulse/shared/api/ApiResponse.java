package org.github.tess1o.geopulse.shared.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Standard API response format.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private String status;
    private String message;
    private T data;
    
    /**
     * Create a success response with data.
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("success", null, data);
    }
    
    /**
     * Create a success response with a message.
     */
    public static ApiResponse<?> success(String message) {
        return new ApiResponse<>("success", message, null);
    }

    
    /**
     * Create an error response with a message.
     */
    public static ApiResponse<?> error(String message) {
        return new ApiResponse<>("error", message, null);
    }

    public static <T> ApiResponse<T> error(String message, T data) {
        return new ApiResponse<>("error", message, data);
    }
}