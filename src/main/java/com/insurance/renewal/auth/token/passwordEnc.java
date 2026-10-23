/**
 *  
 *  @File   : LoginInfo.java
 *  @Version: 1.0
 *  Company : Maan Sarovar Software Private Limited.
 *
 *  Start Date      : 09/22/03
 *  Last Modified   : 10/28/03
 *
 */

package com.insurance.renewal.auth.token;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.util.Base64;



/**
 *
 *  A Java Bean class responsible for handling the 
 *  Login service.
 *
 *  Sample Usage in a jsp file:
 *  <pre>
 *  ------------------------------------------------------------
 * 	   <%@ page import="beans.login.LoginInfo" %>
 *     <jsp:useBean id="login" 
 *                  scope="session" 
 *                  type="beans.login.LoginInfo" 
 *                  beanName="beans.login.LoginInfo" />
 *  ------------------------------------------------------------
 *  @author 	Sriram    Maan Sarovar Software Private Limited.
 *  @version    1.0       22 Sep 2003
 *  </pre>
 *
 *  
 **/
public class passwordEnc {

	private static passwordEnc instance;

	/** The connection is used for getting Database Connection. */
	private Connection connection;

	/** The loginName is used for storing the user name. */
	private String loginName;

	/** The displayName is used for storing the display name. */
	private String displayName;

	/** The loginPassword is used for storing the crypted password. */
	private String loginPassword;



	public void LoginInfo(String userName, String password) {
		// clearError();

		//  this.undatabasify(userName);
		try {
			//crypt the password and check if the undatabasify is ok.
			/*if (!(this.loginPassword.equals(crypt(password.substring(0,3),password))) ) {
				this.loginName = null;
				//setError(crypt(password.substring(0,3),password));

				//   this.status = null;
			}*/
			if (!(this.loginPassword.equals(crypt(password))) ) {
				this.loginName = null;
				//setError(crypt(password.substring(0,3),password));

				//   this.status = null;
			}
		}
		catch (Exception exception) {
			this.loginName = null;
			this.displayName = null;
			this.loginPassword = null;


		}
	}


	/*public  String crypt(String salt, String original) {
		String encryptedString = "";
		try {
			//PasswordService password = new PasswordService();
			encryptedString = encrypt(original); 
		} catch(SystemUnavailableException e) {System.out.println(e.getMessage());}
		return encryptedString;

	}*/
	public  String crypt(String original) {
		String encryptedString = "";
		try {
			//PasswordService password = new PasswordService();
			encryptedString = encrypt(original); 
		} catch(SystemUnavailableException e) {System.out.println(e.getMessage());}
		return encryptedString;

	}


	public synchronized void decrypt(String plaintext) throws SystemUnavailableException
	{
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("SHA"); //step 2
		}
		catch(NoSuchAlgorithmException e) {
			throw new SystemUnavailableException(e.getMessage());
		}
		try {

			md.update(plaintext.getBytes("UTF-8")); //step 3
		}
		catch(UnsupportedEncodingException e) {
			throw new SystemUnavailableException(e.getMessage());
		}
		try{
			byte raw1[] = Base64.getDecoder().decode(plaintext);
			BigInteger test = new BigInteger(1,raw1);
			System.out.println("royal test new decryption from encrtpt method..."+test);
		}
		catch(Exception e) {
			e.printStackTrace();
		}

	}
	public synchronized String encrypt(String plaintext) throws SystemUnavailableException
	{
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("SHA"); //step 2
		}
		catch(NoSuchAlgorithmException e) {
			throw new SystemUnavailableException(e.getMessage());
		}

		try {
			md.update(plaintext.getBytes("UTF-8")); //step 3
		}
		catch(UnsupportedEncodingException e) {
			throw new SystemUnavailableException(e.getMessage());
		}

		byte raw[] = md.digest(); //step 4
		String hash = Base64.getEncoder().encodeToString(raw); //step 5

		return hash;    //step 6


	}


	public static synchronized passwordEnc getInstance() { //step 1
		if(instance == null)
		{
			instance = new passwordEnc(); 
		} 
		return instance;
	}
}