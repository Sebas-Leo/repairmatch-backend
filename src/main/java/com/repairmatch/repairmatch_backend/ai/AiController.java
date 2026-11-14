package com.repairmatch.repairmatch_backend.ai;

import com.repairmatch.repairmatch_backend.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local AI endpoints (CLIENT only). Both run an in-process model: no external
 * service, no API key and no cost per call.
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {
    private final AiService aiService;

    /** "Así entendimos tu problema": appliance, symptom, brand and model from the free description. */
    @PostMapping("/structure-request")
    public AiDtos.StructuredFault structure(@Valid @RequestBody AiDtos.StructureRequest body) {
        return aiService.structure(body.description());
    }

    /** Natural-language search over the client's own requests, with the filters it understood. */
    @PostMapping("/search")
    public AiDtos.SearchResult search(@Valid @RequestBody AiDtos.SearchRequest body) {
        int page = body.page() == null ? 0 : body.page();
        int size = body.size() == null ? PageResponse.DEFAULT_SIZE : body.size();
        return aiService.search(body.query(), page, size);
    }
}
