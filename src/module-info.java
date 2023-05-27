module com.sterndu.DataTransfer {
	exports com.sterndu.data.transfer;
	exports com.sterndu.data.transfer.basic;
	exports com.sterndu.data.transfer.secure;

	requires transitive Encryption;
	requires transitive com.sterndu.MultiCore;
	requires com.sterndu.Util;
}