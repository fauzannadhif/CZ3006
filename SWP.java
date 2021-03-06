/*===============================================================*
 *  File: SWP.java                                               *
 *                                                               *
 *  This class implements the sliding window protocol            *
 *  Used by VMach class					         *
 *  Uses the following classes: SWE, Packet, PFrame, PEvent,     *
 *                                                               *
 *  Author: Professor SUN Chengzheng                             *
 *          School of Computer Engineering                       *
 *          Nanyang Technological University                     *
 *          Singapore 639798                                     *
 *===============================================================*/

import java.util.Timer;
import java.util.TimerTask;

public class SWP {

/*========================================================================*
 the following are provided, do not change them!!
 *========================================================================*/
   //the following are protocol constants.
   public static final int MAX_SEQ = 7; 
   public static final int NR_BUFS = (MAX_SEQ + 1)/2;

   // the following are protocol variables
   private int oldest_frame = 0;
   private PEvent event = new PEvent();  
   private Packet out_buf[] = new Packet[NR_BUFS];

   //the following are used for simulation purpose only
   private SWE swe = null;
   private String sid = null;

   

   //Constructor
   public SWP(SWE sw, String s){
      swe = sw;
      sid = s;
   }

   //the following methods are all protocol related
   private void init(){
      for (int i = 0; i < NR_BUFS; i++){
	   out_buf[i] = new Packet();
      }
   }

   private void wait_for_event(PEvent e){
      swe.wait_for_event(e); //may be blocked
      oldest_frame = e.seq;  //set timeout frame seq
   }

   private void enable_network_layer(int nr_of_bufs) {
      //network layer is permitted to send if credit is available
	   swe.grant_credit(nr_of_bufs);
   }

   private void from_network_layer(Packet p) {
      swe.from_network_layer(p);
   }

   private void to_network_layer(Packet packet) {
	   swe.to_network_layer(packet);
   }

   private void to_physical_layer(PFrame fm)  {
      System.out.println("SWP: Sending frame: seq = " + fm.seq + 
			    " ack = " + fm.ack + " kind = " + 
			    PFrame.KIND[fm.kind] + " info = " + fm.info.data );
      System.out.flush();
      swe.to_physical_layer(fm);
   }

   private void from_physical_layer(PFrame fm) {
      PFrame fm1 = swe.from_physical_layer(); 
	   fm.kind = fm1.kind;
	   fm.seq = fm1.seq; 
	   fm.ack = fm1.ack;
	   fm.info = fm1.info;
   }


/*===========================================================================*
 	implement your Protocol Variables and Methods below: 
 *==========================================================================*/

   private boolean no_nak = true;
   private Timer timer[] = new Timer[NR_BUFS];
	private Timer ack_timer;
   private static final int TIMEOUT = 1000;
	private static final int ACK_TIMEOUT = 500;

   private boolean is_between(int a, int b, int c){
      return (((a <= b) && (b < c)) || ((c < a) && (a <= b)) || ((b < c) && (c < a)));
   }

   private void send_frame(int fk, int frame_nr, int frame_expected, Packet buffer[]){
      PFrame s = new PFrame();
      s.kind = fk;
      if (fk == PFrame.DATA) s.info = buffer[frame_nr % NR_BUFS];
      s.seq = frame_nr;
      s.ack = (frame_expected + MAX_SEQ) % (MAX_SEQ + 1);
      if (fk == PFrame.NAK) no_nak = false;
      to_physical_layer(s);
      if (fk == PFrame.DATA) start_timer(frame_nr);
      stop_ack_timer(); 
   }

   private int circular_inc(int seq){
      return (seq+1) % (MAX_SEQ+1);
   }

   public void protocol6() {
      init();
      
      int i;
      PFrame r = new PFrame();
      Packet in_buf[] = new Packet[NR_BUFS];
      boolean arrived[] = new boolean[NR_BUFS];
      PEvent event = new PEvent(); 

      enable_network_layer(NR_BUFS);
      int ack_expected = 0;
      int next_frame_to_send = 0;
      int frame_expected = 0;
      int too_far = NR_BUFS;
      int nbuffered = 0;
      for (i = 0; i < NR_BUFS; i++) arrived[i] = false;
      for (i = 0; i < NR_BUFS; i++){
	      in_buf[i] = new Packet();
      }

	   while(true) {	
         wait_for_event(event);
	      switch(event.type) {
	         case (PEvent.NETWORK_LAYER_READY):
               nbuffered++;
               from_network_layer(out_buf[next_frame_to_send % NR_BUFS]);
               send_frame(PFrame.DATA, next_frame_to_send, frame_expected, out_buf);
               next_frame_to_send = circular_inc(next_frame_to_send);
               break; 
	         case (PEvent.FRAME_ARRIVAL ):
               from_physical_layer(r);
               if (r.kind == PFrame.DATA){
                  if (r.seq != frame_expected && no_nak){
                     send_frame(PFrame.NAK, 0, frame_expected, out_buf);
                  }
                  else start_ack_timer();
                  if (is_between(frame_expected, r.seq, too_far) && arrived[r.seq % NR_BUFS] == false){
                     arrived[r.seq % NR_BUFS] = true;
                     in_buf[r.seq % NR_BUFS] = r.info;
                     while (arrived[frame_expected % NR_BUFS]){
                        to_network_layer(in_buf[frame_expected % NR_BUFS]);
                        no_nak = true;
                        arrived[frame_expected % NR_BUFS] = false;
                        frame_expected = circular_inc(frame_expected);
                        too_far = circular_inc(too_far);
                        start_ack_timer();
                     } 
                  }
               }

               if ((r.kind == PFrame.NAK) & is_between(ack_expected, (r.ack+1) % (MAX_SEQ+1), next_frame_to_send))
                  send_frame(PFrame.DATA, (r.ack+1) % (MAX_SEQ+1), frame_expected, out_buf);

               while (is_between(ack_expected, r.ack, next_frame_to_send)){
                  nbuffered--;
                  stop_timer(ack_expected % NR_BUFS);
                  ack_expected = circular_inc(ack_expected);
                  enable_network_layer(1);
               }                  
		         break;	   
            case (PEvent.CKSUM_ERR):
               if (no_nak) send_frame(PFrame.NAK, 0, frame_expected, out_buf);
      	      break;  
            case (PEvent.TIMEOUT):
               send_frame(PFrame.DATA, oldest_frame, frame_expected, out_buf); 
	            break; 
	         case (PEvent.ACK_TIMEOUT):
               send_frame(PFrame.ACK, 0, frame_expected, out_buf); 
               break; 
            default: 
		         System.out.println("SWP: undefined event type = " 
                                 + event.type); 
		         System.out.flush();
	      }
      }      
   }

 /* Note: when start_timer() and stop_timer() are called, 
    the "seq" parameter must be the sequence number, rather 
    than the index of the timer array, 
    of the frame associated with this timer, 
   */
 
   private void start_timer(int seq) {
      stop_timer(seq);
		timer[seq % NR_BUFS] = new Timer();
		timer[seq % NR_BUFS].schedule(new NormalTimerTask(seq), TIMEOUT);
   }

   private void stop_timer(int seq) {
      if (timer[seq % NR_BUFS] != null) timer[seq % NR_BUFS].cancel();
   }

   private void start_ack_timer( ) {
      stop_ack_timer();
		ack_timer = new Timer();
		ack_timer.schedule(new AckTimerTask(), ACK_TIMEOUT);
   }

   private void stop_ack_timer() {
      if (ack_timer != null) ack_timer.cancel();
   }

   private class NormalTimerTask extends TimerTask{
		int seq;

		private NormalTimerTask(int seq){
	      this.seq = seq;
	   }

	   @Override
	   public void run(){
		   stop_timer(this.seq);
			swe.generate_timeout_event(seq);
	   }
   }

	/* Ack timer class */
	private class AckTimerTask extends TimerTask{
		@Override
		public void run(){
			stop_ack_timer();
	      swe.generate_acktimeout_event();
		}
	}

}//End of class

/* Note: In class SWE, the following two public methods are available:
   . generate_acktimeout_event() and
   . generate_timeout_event(seqnr).

   To call these two methods (for implementing timers),
   the "swe" object should be referred as follows:
     swe.generate_acktimeout_event(), or
     swe.generate_timeout_event(seqnr).
*/


