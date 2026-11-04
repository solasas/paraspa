package com.sashank.paraspa.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * JSON body for a text-only post. Posts with images are sent as multipart/form-data instead.
 * The service enforces "text or at least one image" for both forms.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePostRequest {
    @Size(max = 2000, message = "Content must be at most 2000 characters")
    private String content;

    /** Up to 3 distinct skills. Not bounded here: the service counts distinct ids, the same way for JSON and multipart. */
    private List<Long> skillIds;
}
