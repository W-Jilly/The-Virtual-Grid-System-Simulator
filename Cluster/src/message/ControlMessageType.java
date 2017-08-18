package message;

/**
 * 
 * Different types of control messages. Feel free to add new message types if you need any. 
 * 
 * @author Niels Brouwers
 *
 */
public enum ControlMessageType {

	// from RM to GS
	ResourceManagerJoin,
	ReplyLoad,
	Replication,
	FailReport,

	// from GS to RM
	RequestLoad,
	JoinAck,
	HeartBeat,
	HeartPulse,

	// both ways
	AddJob,
	
	// from GS to GS
	UpdateLoad,
	
	// from GS to GS
	ProbeGSLoad,

	// from GS to GS
	ReplyGSLoad,	
	RingConnect
}
