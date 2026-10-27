package com.stefan.knowledgeagent.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Profile("rag")
public class KnowledgeCorpusLoader implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(KnowledgeCorpusLoader.class);

    private static final List<CorpusSource> SOURCES = List.of(
            new CorpusSource(
                    "shipping-policy",
                    "Shipping Policy",
                    "1.0",
                    "classpath:knowledge/shipping-policy.md"
            ),
            new CorpusSource(
                    "refund-policy",
                    "Refund Policy",
                    "1.0",
                    "classpath:knowledge/refund-policy.md"
            ),
            new CorpusSource(
                    "warranty-policy",
                    "Warranty Policy",
                    "1.0",
                    "classpath:knowledge/warranty-policy.md"
            )
    );

    private final ResourceLoader resourceLoader;
    private final KnowledgeIngestionService ingestionService;

    public KnowledgeCorpusLoader(
            ResourceLoader resourceLoader,
            KnowledgeIngestionService ingestionService
    ) {

        this.resourceLoader = resourceLoader;
        this.ingestionService = ingestionService;

    }

    @Override
    public void run(ApplicationArguments args) throws IOException {

        for (CorpusSource source : SOURCES) {

            Resource resource =
                    resourceLoader.getResource(source.location());

            String content;

            try (var input = resource.getInputStream()) {

                content = new String(
                        input.readAllBytes(),
                        StandardCharsets.UTF_8
                );
            }

            KnowledgeIngestionService.IngestionResult result =
                    ingestionService.ingest(
                            source.id(),
                            source.name(),
                            source.version(),
                            content
                    );

            log.info(
                    "Knowledge source processed: sourceId={}, version={}, "
                            + "status={}, chunks={}",
                    result.sourceId(),
                    source.version(),
                    result.skipped() ? "unchanged" : "indexed",
                    result.chunkCount()
            );
        }
    }

    private record CorpusSource(
            String id,
            String name,
            String version,
            String location
    ) {
    }
}
