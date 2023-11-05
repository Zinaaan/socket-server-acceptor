package client;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author lzn
 * @date 2023/06/21 14:14
 * @description Tcp Client to test the interaction with Netty tcp server
 */
@Slf4j
public class TcpClient {

    public static byte[] generateBytesData() {
        int command = 9999;
        int version = 1;
        int clientType = 4;
        int messageType = 0x0;
        int appId = 10000;
        String name = "123";
        String imei = UUID.randomUUID().toString();

        // Convert parameters to bytes
        byte[] commandByte = intToBytes(command, 4);
        byte[] versionByte = intToBytes(version, 4);
        byte[] messageTypeByte = intToBytes(messageType, 4);
        byte[] clientTypeByte = intToBytes(clientType, 4);
        byte[] appIdByte = intToBytes(appId, 4);
        byte[] imeiBytes = imei.getBytes(StandardCharsets.UTF_8);
        int imeiLength = imeiBytes.length;
        byte[] imeiLengthByte = intToBytes(imeiLength, 4);
        byte[] password = encrypt().getBytes(StandardCharsets.UTF_8);
        int pwdLength = password.length;
        byte[] pwdLengthByte = intToBytes(pwdLength, 4);

        // Construct the JSON data
        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("appId", appId);
        data.put("clientType", clientType);
        data.put("imei", imei);
//        String jsonData = JSONObject.toJSONString(data);
//        byte[] body = jsonData.getBytes(StandardCharsets.UTF_8);
//        int bodyLen = body.length;
//        byte[] bodyLenBytes = intToBytes(bodyLen, 4);

        // Create the byte array for the request
        return concatArrays(commandByte, versionByte, clientTypeByte,
                messageTypeByte, appIdByte, pwdLengthByte, imeiLengthByte,password, imeiBytes);
    }

    public static byte[] intToBytes(int value, int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[length - 1 - i] = (byte) (value >> (i * 8));
        }
        return bytes;
    }

    public static byte[] concatArrays(byte[]... arrays) {
        int totalLength = 0;
        for (byte[] array : arrays) {
            totalLength += array.length;
        }
        byte[] result = new byte[totalLength];
        int offset = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }

//    public static void main(String[] args) {
//        // Replace with the server's address
//        String serverAddress = "localhost";
//        // Replace with the server's port
//        int serverPort = 9000;
//
//        try {
//            // Connect to the server
//            Socket socket = new Socket(serverAddress, serverPort);
//
//            // Get the output stream of the socket
//            OutputStream outputStream = socket.getOutputStream();
//
//            // Construct your request data as a byte array
//            byte[] requestData = generateBytesData(); // Your request data
//
//            // Send the request data to the server
//            outputStream.write(requestData);
//            outputStream.flush();
//
//            // Close the socket
//            socket.close();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

    public static void main(String[] args) {
        // Replace with the server's address and port
        String serverAddress = "localhost";
        int serverPort = 9000;

        try {
            // Connect to the server
            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.connect(new InetSocketAddress(serverAddress, serverPort));

            // Construct your request data as a byte array
            byte[] requestData = generateBytesData(); // Your request data


            // Read response asynchronously
            while (true) {
                // Send the request data to the server
                ByteBuffer buffer = ByteBuffer.wrap(requestData);
                while (buffer.hasRemaining()) {
                    socketChannel.write(buffer);
                }

                // Create a buffer to receive data
                ByteBuffer responseBuffer = ByteBuffer.allocate(1024); // Adjust buffer size as needed
                responseBuffer.clear();
                int bytesRead = socketChannel.read(responseBuffer);
                if (bytesRead == -1) {
                    break; // No more data
                }

                // Process the received data
                responseBuffer.flip();
                byte[] responseData = new byte[responseBuffer.remaining()];
                responseBuffer.get(responseData);

                String response = new String(responseData);
                log.info("Received response: " + response);
                log.info("----------------------");
                TimeUnit.SECONDS.sleep(1);
            }

            // Close the socket
//            socketChannel.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String encrypt() {
        String secretKey = "abcdeabcdeabcdea"; // 128-bit key
        String plainPassword = "mysecretpassword";

        // Create AES key from secret key
        Key key = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "AES");

        // Encrypt the password
        byte[] encryptedBytes = new byte[0];
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            encryptedBytes = cipher.doFinal(plainPassword.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Encode encrypted password as base64
        String pwd = Base64.getEncoder().encodeToString(encryptedBytes);
        log.info("encrypt password: " + pwd);

        return pwd;
    }
}