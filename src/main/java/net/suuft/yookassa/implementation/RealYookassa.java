package net.suuft.yookassa.implementation;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import net.suuft.yookassa.Yookassa;
import net.suuft.yookassa.exception.BadRequestException;
import net.suuft.yookassa.exception.UnspecifiedShopInformation;
import net.suuft.yookassa.type.Amount;
import net.suuft.yookassa.type.Payment;
import net.suuft.yookassa.type.Refund;
import net.suuft.yookassa.type.collecting.PaymentList;
import net.suuft.yookassa.type.collecting.RefundList;
import net.suuft.yookassa.type.request.PaymentRequest;
import net.suuft.yookassa.type.request.RefundRequest;
import net.suuft.yookassa.utility.JsonUtil;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.UUID;

@AllArgsConstructor
public class RealYookassa implements Yookassa {

    private int shopIdentifier;
    private String shopToken;

    @Override
    public Payment createPayment(@NonNull Amount amount, @NonNull String description, @NonNull String redirectUrl) throws UnspecifiedShopInformation, BadRequestException, IOException {
        return parseResponse(Payment.class, "https://api.yookassa.ru/v3/payments", "POST", JsonUtil.toJson(PaymentRequest.create(amount, redirectUrl, description)));
    }

    @Override
    public Payment getPayment(@NonNull UUID paymentIdentifier) throws UnspecifiedShopInformation, BadRequestException, IOException {
        return parseResponse(Payment.class, "https://api.yookassa.ru/v3/payments/" + paymentIdentifier, "GET", null);
    }

    @Override
    public PaymentList getPayments() throws UnspecifiedShopInformation, BadRequestException, IOException {
        return parseResponse(PaymentList.class, "https://api.yookassa.ru/v3/payments", "GET", null);
    }

    @Override
    public Refund createRefund(@NonNull UUID paymentIdentifier, @NonNull Amount amount) throws UnspecifiedShopInformation, BadRequestException, IOException {
        return parseResponse(Refund.class, "https://api.yookassa.ru/v3/refunds", "POST", JsonUtil.toJson(new RefundRequest(amount, paymentIdentifier)));
    }

    @Override
    public Refund getRefund(@NonNull UUID refundIdentifier) throws UnspecifiedShopInformation, BadRequestException, IOException {
        return parseResponse(Refund.class, "https://api.yookassa.ru/v3/refunds/" + refundIdentifier, "GET", null);
    }

    @Override
    public RefundList getRefunds() throws UnspecifiedShopInformation, BadRequestException, IOException {
        return parseResponse(RefundList.class, "https://api.yookassa.ru/v3/refunds", "GET", null);
    }

    private <T> T parseResponse(@NonNull Class<T> wannableClass, @NonNull String requestAddress, @NonNull String requestMethod, String writableJson) throws IOException, UnspecifiedShopInformation, BadRequestException {
        if (shopIdentifier == 0 || shopToken == null) {
            throw new UnspecifiedShopInformation();

        }
        URL url = new URL(requestAddress);

        HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();

        httpConn.setRequestMethod(requestMethod);

        byte[] message = (shopIdentifier + ":" + shopToken)
                .getBytes(StandardCharsets.UTF_8);
        String basicAuth = DatatypeConverter.printBase64Binary(message);

        httpConn.setRequestProperty("Authorization", "Basic " + basicAuth);

        if (writableJson != null) {
            httpConn.setRequestProperty("Idempotence-Key", UUID.randomUUID().toString());
            httpConn.setRequestProperty("Content-Type", "application/json");
            httpConn.setDoOutput(true);

            OutputStreamWriter writer = new OutputStreamWriter(httpConn.getOutputStream());

            writer.write(writableJson);
            writer.flush();
            writer.close();
            httpConn.getOutputStream().close();
        }

        boolean success = httpConn.getResponseCode() / 100 == 2;
        InputStream responseStream = success
                ? httpConn.getInputStream()
                : httpConn.getErrorStream();
        Scanner s = new Scanner(responseStream).useDelimiter("\\A");

        String response = s.hasNext() ? s.next() : "";

        if (!success) {
            System.out.println(response);
            throw new BadRequestException();
        }

        return JsonUtil.fromJson(response, wannableClass);
    }
}
