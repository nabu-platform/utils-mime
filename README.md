# Why a new library?

There is one standard implementation of mime in java which can be found in java mail.
It's awesome...for mail.

However mime has grown beyond mail and is used for AS1/2/3, HTTP, MTOM + XOP, HTML (forms),...
However the rather large java mail library has a focus on email and it does not (out of the box) offer S/MIME capabilities.

This library was created as a more low level barebones approach of MIME handling.

As an example of the close affinity of java mail mime handling to well...mail, let's look at the javax.mail.Part interface.
It requires a lot of methods that should actually not be in scope:

- getDisposition(): only relevant for mails, not for example AS2
- getDescription(): not sure why this exists
- getFileName(): assumes that there is a file
- getInputStream(): assumes that there is an actual input stream available, multiparts (especially new ones that are unformatted) do not necessarily have this
...

It gets worse when you look at the interface javax.mail.internet.MimePart:

- getContentMD5()
- getContentID()
- getContentLanguage()
...

I'm not saying these methods aren't useful, I'm saying they shouldn't be in the specification of a part.
By all means have a class that can interpret headers according to their RFC standards, don't force it on the generic "part" and "mimepart" specs.
This isn't done out of malice but was presumably designed this way because it is a library primarily for mail but it does hinder reuse in non-mail related fields.

For comparison, take the Part interface for this package:

```java
public interface Part extends Resource {
	public Header [] getHeaders();
}
```

Where the Resource interface is actually described in the resources-api package:

```java
public interface Resource {
	public static final String CONTENT_TYPE_DIRECTORY = "application/directory";
	public String getContentType();
	public String getName();
	public ResourceContainer<?> getParent();
}
```

The neat thing about this is that all Parts are automatically also resources.
Likewise the MultiPart interface extends ResourceContainer:

```java
public interface MultiPart extends Part, ResourceContainer<Part> {}
```

While ContentPart allows you to access the content:

```java
public interface ContentPart extends Part, ReadableResource {}
```

Because of this reuse of the resources api, all mime messages parsed/created by this library can be easily exposed as a file system.
The security of S/MIME is currently handled by a separate utils-security package but may be integrated at some point.

# How to use it?

## Very simple text part (no multipart)

Let's create a simple text mime part:

```java
PlainMimePart newPart = new PlainMimeContentPart(null, IOUtils.wrap("this is just some text".getBytes(), true));
newPart.setHeader(new MimeHeader("Content-Type", "text/plain"));
MimeFormatter formatter = new MimeFormatter();
Container<ByteBuffer> bytes = IOUtils.newByteBuffer();
formatter.format(newPart, bytes);
System.out.println(new String(IOUtils.toBytes(bytes)));
```

This will print:

```
Content-Type: text/plain
Content-Transfer-Encoding: quoted-printable

this is just some text
```

The reason that the content-transfer-encoding header is injected is because the formatter by default does not allow binary content, if you set ```formatter.setAllowBinary(true);``` you will get:

```
Content-Type: text/plain

this is just some text
```

## Chunking

If we add the following lines to the previous example we get chunked output:

```java
newPart.setHeader(new MimeHeader("Transfer-Encoding", "chunked"));
MimeFormatter formatter = new MimeFormatter();
formatter.setAllowBinary(true);
formatter.setChunkSize(10);
```

I set the chunk size arbitrarily low to show you actual chunking on such limited data:

```
Content-Type: text/plain
Transfer-Encoding: chunked

a
this is ju
a
st some te
2
xt
0
```

## Very simple multipart

```java
PlainMimeMultiPart newPart = new PlainMimeMultiPart(null);
newPart.setHeader(new MimeHeader("Content-Type", "multipart/mixed"));
newPart.addChild(
	new PlainMimeContentPart(null, IOUtils.wrap("this is just some text".getBytes(), true),
			new MimeHeader("Content-Type", "text/plain"),
			new MimeHeader("Content-Transfer-Encoding", "base64")));
newPart.addChild(
	new PlainMimeContentPart(newPart, IOUtils.wrap("<html><body>something hére</body></html>".getBytes(), true),
			new MimeHeader("Content-Type", "text/html")));
MimeFormatter formatter = new MimeFormatter();
Container<ByteBuffer> bytes = IOUtils.newByteBuffer();
formatter.format(newPart, bytes);
System.out.println(new String(IOUtils.toBytes(bytes)));
```

The output of this piece of code is:

```
MIME-Version: 1.0
Content-Type: multipart/mixed;
	boundary=-=part.bf64dd3b7c5fec297397f828545e833b824c8c5

---=part.bf64dd3b7c5fec297397f828545e833b824c8c5
Content-Type: text/plain
Content-Transfer-Encoding: base64

dGhpcyBpcyBqdXN0IHNvbWUgdGV4dA==

---=part.bf64dd3b7c5fec297397f828545e833b824c8c5
Content-Type: text/html
Content-Transfer-Encoding: quoted-printable

<html><body>something h=C3=A9re</body></html>

---=part.bf64dd3b7c5fec297397f828545e833b824c8c5--
```

As you can see, the formatter will automatically perform the base64 encoding you requested and will automatically encode the content (using quoted-printable only for 'largely' ascii text, otherwise it uses base64) in the second part because binary is still off in this example.

While content-transfer-encoding and content-encoding/transfer-encoding are a confusing mix when working with different environments, the formatter simply tries to do what you want it to:

```java
newPart.addChild(
		new PlainMimeContentPart(null, IOUtils.wrap("this is just some text".getBytes(), true),
				new MimeHeader("Content-Type", "text/plain"),
				new MimeHeader("Content-Transfer-Encoding", "base64"),
				new MimeHeader("Content-Encoding", "gzip")));
```

This will generate a part like:

```
Content-Type: text/plain
Content-Transfer-Encoding: base64
Content-Encoding: gzip

H4sIAAAAAAAAACvJyCxWAKKs0uISheL83FSFktSKEgCOkttmFgAAAA==
```

## S/MIME

If you want to encrypt a part that you want to send, it's as easy as doing:

```java
Part encryptedPart = MimeUtils.encrypt(myPart, certificate);
```

Signing is equally easy where "myKey" is the alias of the key you want to use in the keystore:

```java
MultiPart signedPart = MimeUtils.sign(myPart, SignatureType.SHA256WITHRSA, keyStore, "myKey");
```

More examples can be found in the unit tests where keystores are created on the fly and used to format and subsequently parse mime parts.