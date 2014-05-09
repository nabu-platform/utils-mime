package be.nabu.utils.mime.impl;

public enum EncryptionAlgorithm {
	GOST3410("1.2.643.2.2.20"),
	ECGOST3410("1.2.643.2.2.19"),
	RSA("1.2.840.113549.1.1.1"),
	RSA_PSS("1.2.840.113549.1.1.10"),
	DSA("1.2.840.10040.4.3"),
	ECDSA("1.2.840.10045.4.1")
	;
	
	private String oid;
	
	private EncryptionAlgorithm(String oid) {
		this.oid = oid;
	}
	
	public String getOID() {
		return oid;
	}
}
