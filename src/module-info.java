module com.sterndu.DataTransfer {
	exports com.sterndu.data.transfer;

	requires transitive Encryption;
	requires transitive com.sterndu.Util;
}