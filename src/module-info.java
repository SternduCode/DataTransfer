module com.sterndu.DataTransfer {
	exports com.sterndu.data.transfer;

	requires transitive com.sterndu.Encryption;
	requires transitive com.sterndu.MultiCore;
	requires kotlin.stdlib;
	requires java.logging;

	requires kotlinx.coroutines.core;
}
