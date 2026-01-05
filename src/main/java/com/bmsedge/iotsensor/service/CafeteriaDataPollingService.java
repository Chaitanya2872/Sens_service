package com.bmsedge.iotsensor.service;

import com.bmsedge.iotsensor.dto.CafeteriaQueueDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Service to poll cafeteria queue data from Node.js server
 * and save it to the dedicated cafeteria_queue table
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CafeteriaDataPollingService {

    private final CafeteriaQueueService cafeteriaQueueService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${cafeteria.nodejs.url:http://172.105.62.238:3000}")
    private String nodejsUrl;

    @Value("${cafeteria.polling.enabled:true}")
    private boolean pollingEnabled;

    /**
     * Poll Node.js server every 30 seconds for latest queue data
     */
    @Scheduled(cron = "*/30 * * * * *")  // Every 30 seconds
    public void pollCafeteriaData() {
        if (!pollingEnabled) {
            return;
        }

        try {
            log.debug("Polling cafeteria data from Node.js: {}", nodejsUrl);

            String endpoint = nodejsUrl + "/current-queue";
            String response = restTemplate.getForObject(endpoint, String.class);

            if (response == null || response.isEmpty()) {
                log.warn("Empty response from Node.js server");
                return;
            }

            // Parse response
            @SuppressWarnings("unchecked")
            Map<String, Object> queueData = objectMapper.readValue(response, Map.class);

            // Create DTO
            CafeteriaQueueDTO dto = CafeteriaQueueDTO.builder()
                    .twoGoodQ((Integer) queueData.get("TwoGoodQ"))
                    .uttarDakshinQ((Integer) queueData.get("UttarDakshinQ"))
                    .tandoorQ((Integer) queueData.get("TandoorQ"))
                    .twoGoodT((String) queueData.get("TwoGoodT"))
                    .uttarDakshinT((String) queueData.get("UttarDakshinT"))
                    .tandoorT((String) queueData.get("TandoorT"))
                    .build();

            // Save to cafeteria_queue table
            cafeteriaQueueService.saveCafeteriaQueueData(dto);

            log.info("✅ Successfully polled and saved cafeteria data - TwoGood:{}, UttarDakshin:{}, Tandoor:{}",
                    dto.getTwoGoodQ(), dto.getUttarDakshinQ(), dto.getTandoorQ());

        } catch (Exception e) {
            log.error("❌ Error polling cafeteria data from Node.js: {}", e.getMessage());
        }
    }

    /**
     * Test connection to Node.js server
     */
    public Map<String, Object> testConnection() {
        try {
            String endpoint = nodejsUrl + "/health";
            String response = restTemplate.getForObject(endpoint, String.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> health = objectMapper.readValue(response, Map.class);

            return Map.of(
                    "status", "connected",
                    "nodejsUrl", nodejsUrl,
                    "nodejsHealth", health
            );
        } catch (Exception e) {
            return Map.of(
                    "status", "disconnected",
                    "nodejsUrl", nodejsUrl,
                    "error", e.getMessage()
            );
        }
    }
}