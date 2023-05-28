module com.sterndu.DataTransfer {
	exports com.sterndu.data.transfer;
	exports com.sterndu.data.transfer.basic;
	exports com.sterndu.data.transfer.secure;

	requires transitive com.sterndu.Encryption;
	requires transitive com.sterndu.Util; 
	requires transitive com.sterndu.MultiCore;
}