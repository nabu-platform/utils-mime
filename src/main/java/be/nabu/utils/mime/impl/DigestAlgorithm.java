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

public enum DigestAlgorithm {
	SHA1("1.3.14.3.2.26"),
	MD5("1.2.840.113549.2.5"),
	SHA256("2.16.840.1.101.3.4.2.1"),
	SHA384("2.16.840.1.101.3.4.2.2"),
	SHA512("2.16.840.1.101.3.4.2.3"),
	SHA224("2.16.840.1.101.3.4.2.4"),
	GOST3411("1.2.643.2.2.9"),
	RIPEMD160("1.3.36.3.2.1"),
	RIPEMD128("1.3.36.3.2.2"),
	RIPEMD256("1.3.36.3.2.3")
	;
	
	private String oid;
	
	private DigestAlgorithm(String oid) {
		this.oid = oid;
	}
	
	public String getOID() {
		return oid;
	}
}
