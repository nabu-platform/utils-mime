/*
* Copyright (C) 2014 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

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
