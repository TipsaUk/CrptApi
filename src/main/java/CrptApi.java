import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.fluent.Response;
import org.apache.hc.core5.http.ContentType;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class CrptApi {

    private final Object lock = new Object();
    private final Map<Class<?>, String> apiUrlMap = new HashMap<>();
    private final ObjectMapper objectMapper;
    private final BlockingQueue<ApiRequestData> requestQueue;
    private final int requestLimit;
    private final Duration timeInterval;
    private int requestsCount;
    private Instant lastRequestTime;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.objectMapper = new ObjectMapper();
        this.requestQueue = new LinkedBlockingQueue<>();
        this.requestLimit = requestLimit;
        this.timeInterval = Duration.ofMillis(timeUnit.toMillis(1));
        this.requestsCount = 0;
        this.lastRequestTime = Instant.now();

        // предполагаем что url запроса зависит от вида документа
        apiUrlMap.put(DocumentGoodsDto.class, "https://ismp.crpt.ru/api/v3/lk/documents/create");
        startRequestProcessor();
    }

    public void sendDocument(Document document, String signature) {
        requestQueue.add(new ApiRequestData(document, signature));
    }

    private void sendApiRequest(ApiRequestData apiRequestData) {
        String apiUrl = getUrlForDocument(apiRequestData);
        Request request = Request.post(apiUrl);
        request.addHeader("Signature", apiRequestData.getSignature());
        try {
        request.bodyString(objectMapper.writeValueAsString(apiRequestData.getDocument())
                    , ContentType.APPLICATION_JSON);
            Response result = request.execute();
            // обработка результата запроса
        } catch (IOException e) {
            throw new ApiRequestException("Error!", e);
        }
    }

    private String getUrlForDocument(ApiRequestData apiRequestData) {
        Class<?> documentType = apiRequestData.getDocument().getClass();
        String apiUrl = apiUrlMap.get(documentType);
        if (apiUrl == null) {
            throw new IllegalArgumentException("Неподдерживаемый тип документа: " + documentType.getName());
        }
        return apiUrl;
    }

    private void startRequestProcessor() {
        new Thread(() -> {
            while (true) {
                try {
                    ApiRequestData apiRequestData = requestQueue.take();
                    checkRateLimit();
                    sendApiRequest(apiRequestData);
                    updateRateLimit();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }).start();
    }

    private void checkRateLimit() throws InterruptedException {
        synchronized (lock) {
            Instant currentTime = Instant.now();
            Duration elapsedTime = Duration.between(lastRequestTime, currentTime);
            if (elapsedTime.compareTo(timeInterval) > 0) {
                requestsCount = 0;
                lastRequestTime = currentTime;
            }
            if (requestsCount >= requestLimit) {
                lock.wait(timeInterval.toMillis());
            }
        }
    }
    private void updateRateLimit() {
        synchronized (lock) {
            requestsCount++;
            lock.notifyAll();
        }
    }

    private class ApiRequestData {
        private final Document document;
        private final String signature;

        public ApiRequestData(Document document, String signature) {
            this.document = document;
            this.signature = signature;
        }

        public Document getDocument() {
            return document;
        }

        public String getSignature() {
            return signature;
        }
    }

    public interface Document {
    }

    public static class DocumentGoodsDto implements Document {

        private DescriptionDto descriptionDto;
        private String doc_id;
        private String doc_status;
        private DocType doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private Date production_date;
        private String production_type;
        private List<ProductDto> productDtos;
        private Date reg_date;
        private String reg_number;

        public DescriptionDto getDescription() {
            return descriptionDto;
        }

        public void setDescription(DescriptionDto descriptionDto) {
            this.descriptionDto = descriptionDto;
        }

        public String getDoc_id() {
            return doc_id;
        }

        public void setDoc_id(String doc_id) {
            this.doc_id = doc_id;
        }

        public String getDoc_status() {
            return doc_status;
        }

        public void setDoc_status(String doc_status) {
            this.doc_status = doc_status;
        }

        public DocType getDoc_type() {
            return doc_type;
        }

        public void setDoc_type(DocType doc_type) {
            this.doc_type = doc_type;
        }

        public boolean isImportRequest() {
            return importRequest;
        }

        public void setImportRequest(boolean importRequest) {
            this.importRequest = importRequest;
        }

        public String getOwner_inn() {
            return owner_inn;
        }

        public void setOwner_inn(String owner_inn) {
            this.owner_inn = owner_inn;
        }

        public String getParticipant_inn() {
            return participant_inn;
        }

        public void setParticipant_inn(String participant_inn) {
            this.participant_inn = participant_inn;
        }

        public String getProducer_inn() {
            return producer_inn;
        }

        public void setProducer_inn(String producer_inn) {
            this.producer_inn = producer_inn;
        }

        public Date getProduction_date() {
            return production_date;
        }

        public void setProduction_date(Date production_date) {
            this.production_date = production_date;
        }

        public String getProduction_type() {
            return production_type;
        }

        public void setProduction_type(String production_type) {
            this.production_type = production_type;
        }

        public List<ProductDto> getProducts() {
            return productDtos;
        }

        public void setProducts(List<ProductDto> productDtos) {
            this.productDtos = productDtos;
        }

        public Date getReg_date() {
            return reg_date;
        }

        public void setReg_date(Date reg_date) {
            this.reg_date = reg_date;
        }

        public String getReg_number() {
            return reg_number;
        }

        public void setReg_number(String reg_number) {
            this.reg_number = reg_number;
        }
    }

    public enum DocType {
        LP_INTRODUCE_GOODS
    }

    public static class DescriptionDto {
        private String participantInn;

        public DescriptionDto(String participantInn) {
            this.participantInn = participantInn;
        }

        public String getParticipantInn() {
            return participantInn;
        }

        public void setParticipantInn(String participantInn) {
            this.participantInn = participantInn;
        }
    }


    public static class ProductDto {

        private String certificate_document;
        private String certificate_document_date;
        private String certificate_document_number;
        private String owner_inn;
        private String producer_inn;
        private Date production_date;
        private String tnved_code;
        private String uit_code;
        private String uitu_code;

        public String getCertificate_document() {
            return certificate_document;
        }

        public void setCertificate_document(String certificate_document) {
            this.certificate_document = certificate_document;
        }

        public String getCertificate_document_date() {
            return certificate_document_date;
        }

        public void setCertificate_document_date(String certificate_document_date) {
            this.certificate_document_date = certificate_document_date;
        }

        public String getCertificate_document_number() {
            return certificate_document_number;
        }

        public void setCertificate_document_number(String certificate_document_number) {
            this.certificate_document_number = certificate_document_number;
        }

        public String getOwner_inn() {
            return owner_inn;
        }

        public void setOwner_inn(String owner_inn) {
            this.owner_inn = owner_inn;
        }

        public String getProducer_inn() {
            return producer_inn;
        }

        public void setProducer_inn(String producer_inn) {
            this.producer_inn = producer_inn;
        }

        public Date getProduction_date() {
            return production_date;
        }

        public void setProduction_date(Date production_date) {
            this.production_date = production_date;
        }

        public String getTnved_code() {
            return tnved_code;
        }

        public void setTnved_code(String tnved_code) {
            this.tnved_code = tnved_code;
        }

        public String getUit_code() {
            return uit_code;
        }

        public void setUit_code(String uit_code) {
            this.uit_code = uit_code;
        }

        public String getUitu_code() {
            return uitu_code;
        }

        public void setUitu_code(String uitu_code) {
            this.uitu_code = uitu_code;
        }
    }

    public static class ApiRequestException extends RuntimeException {
        public ApiRequestException(String message, Throwable cause) {
            super(message, cause);
        }
    }


}
