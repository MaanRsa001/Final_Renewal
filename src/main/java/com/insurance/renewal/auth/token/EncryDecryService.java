package com.insurance.renewal.auth.token;

import javax.crypto.Cipher;

import org.springframework.stereotype.Service;

import jakarta.xml.bind.DatatypeConverter;

@Service
public class EncryDecryService {

private static final String FORMAT = "ISO-8859-1";
	
	public static String encrypt(String unencryptedString) throws Exception {

        String encryptedString = null;
        CryptoConfig.getCipher().init(Cipher.ENCRYPT_MODE, CryptoConfig.getKey());
        byte[] plainText = unencryptedString.getBytes(FORMAT);
        byte[] encryptedText = CryptoConfig.getCipher().doFinal(plainText);
        encryptedString = DatatypeConverter.printBase64Binary(encryptedText);
        return encryptedString;
        
	
	}
	
	public static String decrypt(String encryptedString)  throws Exception {

        String decryptedText = null;
        CryptoConfig.getCipher().init(Cipher.DECRYPT_MODE, CryptoConfig.getKey());
        byte[] encryptedText = DatatypeConverter.parseBase64Binary(encryptedString.replace(" ", "+"));
        byte[] plainText = CryptoConfig.getCipher().doFinal(encryptedText);
        decryptedText = new String(plainText);

        return decryptedText;
    }
	
}
