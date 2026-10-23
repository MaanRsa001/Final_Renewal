package com.insurance.renewal.auth.token;

import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESedeKeySpec;

public class CryptoConfig {
    private static Cipher cipher;
    private static SecretKey key;
	
	private static CryptoConfig instance = new CryptoConfig();
	
	private CryptoConfig(){
		
	}
	public CryptoConfig getInstance(){
		if(instance==null)
			instance=new CryptoConfig();
		return instance;
	}
	
	public static SecretKey getKey() {
		return key;
	}
	
	public static Cipher getCipher() {
		return cipher;
	}

	static{
		try{
			final String FORMAT = "ISO-8859-1";
		    final String DESEDE_ENCRYPTION_SCHEME = "DESede"; 
			String secretKey = "rsaassociatesiLinkELoungeegnuoLEkniLisetaicossaasr";
			KeySpec ks = new DESedeKeySpec(secretKey.getBytes(FORMAT));
			SecretKeyFactory skf = SecretKeyFactory.getInstance(DESEDE_ENCRYPTION_SCHEME);
			cipher = Cipher.getInstance(DESEDE_ENCRYPTION_SCHEME);
			key = skf.generateSecret(ks);
		}catch(Exception e){
			e.printStackTrace();
		}
	}
}
