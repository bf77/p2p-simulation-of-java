import javax.swing.*;
import java.awt.RenderingHints;
import java.awt.BasicStroke;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.Point;
import java.awt.geom.*;
import java.util.Random;
import java.util.ArrayList;

public class Simulation extends JPanel{

    static final int WIDTH = 1900;
    static final int HEIGHT = 800;
    static final float STROKE = 2.0f;

    static final int MAX_LAYER = 10;

    static final int MAX_NODE = 1022;

    //Including Origin Source
    static final int PAIRS_NUM = ( MAX_NODE * (MAX_NODE + 1) )/2;

    //1~200 The Bound of the BW
    static final int BOUND = 199;

    //20~200 The Bound of the BW
    static final int OS_BOUND = 180;

    static final int MAX_PACKET_BYTE = 1500;

    int CAPACITY = 400;
    
    int CAPACITY_INITVALUE = 100;

    long TIMESTAMP = 0;

    long CURRENT_TIME = 0;

    long PRE_TIMESTAMP;
    
    //Bandwidth
    double[] BW_PAIRS;

    //Origin Source
    OriginSrc OS;

    //ID of Origin Source
    static final int OS_ID = MAX_NODE;

    //for eclipse
    static final double OS_R = 30.0d;

    //10Mbps ->  buffer[Byte/ms]
    static final double BUFFER = 10*1000*1.0 / 8;
    
    //Node
    Node[] NODES;

    //for eclipse
    static final double NODE_R = 5.0d;

    static final int TMP_MAX_CHILD = 2;

    static final int CACHE_TLV = 1000;

    //The Bound of the time to join ( 0~10 min )
    static final int BOUND_TIME_JOIN = 1000 * 60;

    double DEPART_INTERVAL;

    ArrayList<IntegerList> LAYER_LIST;

    public static void main(String[] args){

	JFrame frame = new JFrame();

	Simulation sim = new Simulation();
	frame.getContentPane().add(sim);	

	frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	frame.setBounds(10, 10, sim.WIDTH , sim.HEIGHT );
	frame.setTitle("Simulation");
	frame.setVisible(true);

	sim.LAYER_LIST = new ArrayList<IntegerList>(sim.MAX_LAYER);
	sim.initArrayLL( sim.LAYER_LIST , sim.MAX_LAYER+1 );
		    
	sim.osInitialize();
	sim.nodesInitialize();
	
	sim.TIMESTAMP = System.currentTimeMillis();
	while(true){
	    
	    if( sim.PRE_TIMESTAMP != sim.TIMESTAMP ){
		sim.nodeParticipation( sim.LAYER_LIST );
		sim.nodeStreaming( sim.TIMESTAMP - sim.PRE_TIMESTAMP );
	    }

	    sim.repaint();
	    sim.timestampUpdate();

	}

    }

    public void osInitialize(){

	//--Random--
	Random rnd = new Random();
	
	OS = new OriginSrc();

	OS.capacity = rnd.nextDouble()*CAPACITY + CAPACITY_INIT;
	System.out.println( "Origin Source:TLV " + OS.BW_tlv );

	OS.child_num = 0;
	OS.child_id = new ArrayList<Integer>();

	OS.pos = new Point2D.Double( 0.0d , 0.0d );

	OS.start_block_id = 0;
	OS.end_block_id = 0;

	OS.buffer = BUFFER;
	
    }
    
    public void nodesInitialize(){
	
	//--Random--
	Random rnd = new Random();

	//-Bandwidth between pairs--
	BW_PAIRS = new double[PAIRS_NUM];

	int cnt = 0;

	//Including Origin Source
	for( int i=0 ; i<(MAX_NODE) ; i++ ){

	    for( int j=(i+1) ; j<(MAX_NODE + 1) ; j++ ){
		
		//1~X Mbps
		BW_PAIRS[cnt] = rnd.nextDouble()*BOUND + 1;
		System.out.println("Pairs ["+i+","+j+"]:"+BW_PAIRS[i]);
		cnt++;

	    }

	}

	//--Node--
	NODES = new Node[MAX_NODE];
	
	for( int i=0 ; i<MAX_NODE ; i++ ){

	    //init
	    NODES[i] = new Node();

	    //100~500 Mbps
	    NODES[i].capacity = rnd.nextDouble()*CAPACITY + CAPCACITY_INITVALUE;

	    //0~x min
	    NODES[i].timestamp_to_join = rnd.nextInt(BOUND_TIME_JOIN);

	    //Color
	    NODES[i].color = rnd.nextInt()*10000000;

	    //Init value
	    NODES[i].layer = 0;
       	    NODES[i].cache = 0;
	    NODES[i].pre_depart_timestamp = 0;
	    NODES[i].parent_id = -1;
	    NODES[i].child_num = 0;
	    NODES[i].start_block_id = -1;
	    NODES[i].end_block_id = 0;
	    NODES[i].delay = 0.0d;
	    NODES[i].max_down_Bpms = 0.0d;
	    NODES[i].is_begin_play = false;
	    NODES[i].is_begin_stream = false;
	    

	    //Timestamp
	    NODES[i].timestamp = TIMESTAMP;
	    
	    //The position
	    NODES[i].pos = new Point2D.Double( 0.0d , 0.0d );

	    //Initialize
	    NODES[i].child_id = new ArrayList<Integer>();

	    System.out.print("Node "+i+":TLV "+NODES[i].BW_tlv);
	    System.out.println(" Timestamp to join "+NODES[i].timestamp_to_join);

	}
	
    }


    public void timestampUpdate(){

	PRE_TIMESTAMP = TIMESTAMP;
	TIMESTAMP = System.currentTimeMillis(); 

	//Timestamp have not been changed
	if( PRE_TIMESTAMP == TIMESTAMP )
	    return;

	CURRENT_TIME += TIMESTAMP - PRE_TIMESTAMP; 
	
	System.out.println("Time:"+CURRENT_TIME+"ms "+CURRENT_TIME/1000+"s");
	System.out.println("Timestamp:"+TIMESTAMP+"ms");
	System.out.println("Previous timestamp:"+PRE_TIMESTAMP+"ms");

    }

    public void initArrayLL( ArrayList<IntegerList> ll , int length ){

	for( int i=0 ; i<length ; i++ )
	    ll.add( new IntegerList() );

	//layer 0
	ll.get(0).add(OS_ID);

    }
    
    public void nodeParticipation( ArrayList<IntegerList> layer_list , long dt_ms ){

	System.out.println("Participating...");

	for( int id=0 ; id<MAX_NODE ; id++ ){

	    //Whether Nodes can join
	    if( CURRENT_TIME < NODES[id].timestamp_to_join )
		continue;
	    
	    //Whether there is a parent of the node
	    if( NODES[id].parent_id != -1 )
		continue;
	    
	    Boolean is_has_partipated = false;
	    for( int layer=0 ; layer<=MAX_LAYER ; layer++ ){
		
		//layer 1
		if( layer==0 ){
		    
		    OS.start_block_id = (CURRENT_TIME - dt_ms) * BUFFER / MAX_PACKET_BYTE;
		    OS.end_block_id = CURRENT_TIME * BUFFER / MAX_PACKET_BYTE;		    

		    if( OS.child_num < TMP_MAX_CHILD ){

			OS.child_num += 1;
			OS.child_id.add(id);
			
			NODES[id].parent_id = OS_ID;
			NODES[id].layer = layer + 1;
			NODES[id].start_block_id = OS.end_block_id;
			NODES[id].is_begin_streaming = true;
			
			//Store node's id to the first layer
			layer_list.get(layer+1).add(id);
			
			System.out.println("Node "+id+" on Layer "+ (layer+1));
			break;

		    }
		    
		}//layer 2~10
		else{
		    
		    //The number of nodes on the layer
			int node_num_onlayer = layer_list.get(layer).size();
			
			for( int id_onlayer=0 ; id_onlayer<node_num_onlayer ; id_onlayer++ ){
			    
			    int parent_id = layer_list.get(layer).get(id_onlayer);
			    
			    if( NODES[parent_id].child_num < TMP_MAX_CHILD ){
				
				//Processing to parent
				NODES[parent_id].child_num += 1;
				NODES[parent_id].child_id.add(id);
				
				//Processing to child
				NODES[id].parent_id = parent_id;
				NODES[id].layer = layer + 1;
				
				//Regist id to layer_list
				layer_list.get(layer+1).add(id);
				
				//Print
				System.out.println("Node "+id+" on Layer "+(layer+1));
				is_has_partipated = true;
				break;
				
			    }
			    
			}//end one layer's for
			
			if( is_has_partipated )
			    break;
			
		}//end layer!=0 else
		
	    }//end layers' for
	    	    
	}//end all nodes for
	
	System.out.println("All nodes have participated...");

    }

    
    public double nodeCombinationBW( int n , int m ){

	int ret = 0;

	if( n < m ){

	    for( int i=0 ; i<n ; i++ )
		ret += ( MAX_NODE - i );

	    ret += (m - n);
	    
	}   
	else{
	    
	    for( int i=0 ; i<m ; i++ )
		ret += ( MAX_NODE - i );

	    ret += (n - m);

	}

	return BW_PAIRS[ret];

    }
    
    

    public void nodeStreaming( ArrayList<IntegerList> layer_list , long dt_ms ){
		
	for(int layer=0 ; layer<=MAX_LAYER ; layer++ ){
	    
	    int node_num_onlayer = layer_list.get(layer).size();
	    
	    //-- Layer 0 (Origin Source) --
	    if( layer==0 ){
		
		double max_capacity_Bpms = ( OS.capacity * 1000 / 8 ) / OS.child_num;
		
		//Processing to calculate cache
		for( int i=0 ; i<OS.child_num ; i++ ){
		    
		    int child_id = OS.child_id.get(i);
		    double pair_Bpms = nodeCombinationBW( child_id , OS_ID ) * 1000 / 8 ;

		    //Determine the min value
		    NODES[child_id].max_down_Bpms = Math.min( max_capacity_Bpms , pair_Bpms );
		    NODES[child_id].max_down_Bpms = Math.min( NODES[child_id].max_down_Bpms , BUFFER );
		    
		    
		}//end for childnum
		
	    }//-- Layer 1~X --
	    else{
		
		for( int id_onlayer=0 ; id_onlayer<node_num_onlayer ; id_onlayer++ ){
		    
		    int id = LAYER_LIST.get(layer).get(id_onlayer);
		    //The number of nodes's child and the number of the parent 
		    double max_capacity_Bpms = ( NODES[id].capacity * 1000 / 8 ) / ( NODES[id].child_num + 1 );

		    //When participating
		    if( !NODES[id].is_begin_streaming ){
			
			NODES[id].is_begin_streaming = true;
			System.out.println("NODE "+id+": Begin streaming ");
			continue;

		    }

		    //-id processing-
		    
		    if( NODES[id].cache > CACHE_TLV )
			NODES[child_id].is_begin_play = true;
		    
		    double downloaded_data = Math.min( max_capacity_Bpms , NODES[id].max_down_Bpms ) * dt_ms;
		    int dt_block_id = downloaded_data / MAX_PACKET_BYTE;
		    
		    NODES[id].total_buffer += downloaded_data;
		    NODES[id].cache +=  dt_block_id;
		    NODES[id].end_block_id = NODES[id].next_block_id;
		    NODES[id].next_block_id += dt_block_id;
		    
		    //For output
		    if( layer==1 )
			System.out.println("Total buffer node "+id+":"+NODES[id].total_buffer);

		    //Processing to calculate cache
		    for( int i=0 ; i<NODES[id].child_num ; i++ ){
			
			int child_id = NODES[id].child_id.get(i);
			double pair_Bpms = nodeCombinationBW( child_id , id ) * 1000 / 8 ;
			
			

			//Check stream flag
			if( !NODES[id].is_begin_stream )
			    continue;
			    
			//Decide down Bpms to the lower layer
			if( max_down_Bpms < pair_Bpms ){
			    
			    if( max_down_Bpms > BUFFER )
				NODES[child_id].max_down_Bpms = BUFFER;
			    else
				NODES[child_id].max_down_Bpms = max_down_Bpms;
			    
			}else{
			    
			    if( pair_Bpms > BUFFER )
				NODES[child_id].max_down_Bpms = BUFFER;
			    else
				NODES[child_id].max_down_Bpms = pair_Bpms;
			    
			}
			
			
			
						
		    }//end i for
		    
		}// end id_onlayer for
		
	    }//end else
	    
	}//end layer 
	
    }//end function
    
    /*
    public void nodeReconnect(){

	for( int layer=0; layer<MAX_LAYER ; layer++ ){

	    int node_num_onlayer = LAYER_LIST.get(layer).size();
		
	    for( int id_onlayer=0 ; id_onlayer<node_num_onlayer ; id_onlayer++ ){

		int id = LAYER_LIST.get(layer).get(id_onlayer);
		if( !(NODES[id].is_begin_stream) )
		    continue;
		
		if( NODES[id].cache < CACHE_TLV ){

		    
   
		}

	    }

	}

    }
    */

    @Override
    public void paintComponent(Graphics g){

	//Clear the window
	super.paintComponent(g);

	Random rnd = new Random();

	Graphics2D g2 = (Graphics2D)g;

	//Antialiasing
	g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, 
			    RenderingHints.VALUE_ANTIALIAS_ON);

	BasicStroke stroke = new BasicStroke(this.STROKE);
	g2.setStroke(stroke);	
	
	for( int layer=0 ; layer<= this.MAX_LAYER ; layer++ ){


	    if( layer==0 ){
		
		//Set the position of Origin Source
		OS.pos.setLocation( 50.0d + (this.WIDTH -150)/2.0 , 30.0d );

		//g2.setPaint(Color.BLACK);
		//g2.drawString("--Layer "+layer+"--", this.WIDTH - 150 , 70*layer );
		
		g2.setPaint(Color.BLUE);
		Ellipse2D.Double origin_src = 
		    new Ellipse2D.Double( this.OS.pos.getX() - (this.OS_R/2) , this.OS.pos.getY() - (this.OS_R/2), this.OS_R , this.OS_R );
		g2.fill(origin_src);
		
	    }else{

		g2.setPaint(Color.BLACK);
		g2.drawString("--Layer "+layer+"--", this.WIDTH - 150 , 70*layer );
		
		int node_num_onlayer = this.LAYER_LIST.get(layer).size();
		double width_onlayer = (this.WIDTH-150-50)*1.0 / (node_num_onlayer+1);
		
		for( int id_onlayer=0; id_onlayer<node_num_onlayer ; id_onlayer++ ){
		    
		    int id = this.LAYER_LIST.get(layer).get(id_onlayer);
		    
		    g2.setPaint(new Color(NODES[id].color));
		    
		    this.NODES[id].pos.setLocation( 50.0d + (id_onlayer+1)*width_onlayer , 70*layer );
		    
		    int parent_id = this.NODES[id].parent_id;
		    
		    if( parent_id == this.OS_ID ){
			
			g2.draw(new Line2D.Double( this.NODES[id].pos.getX() , this.NODES[id].pos.getY() , this.OS.pos.getX() , this.OS.pos.getY() ));
			
		    }else{
			
			g2.draw(new Line2D.Double( this.NODES[id].pos.getX() , this.NODES[id].pos.getY() , this.NODES[parent_id].pos.getX() , this.NODES[parent_id].pos.getY() ));
			
		    }		
		    
		    Ellipse2D.Double node = 
			new Ellipse2D.Double( this.NODES[id].pos.getX() - (this.NODE_R/2), this.NODES[id].pos.getY() - (this.NODE_R/2), this.NODE_R , this.NODE_R );
		    g2.fill(node);
		    
		}//end id_onlayer for
		
	    }//end else
	    
	}//end layer for

    }//end paintComponent()

}//end class

class IntegerList extends ArrayList<Integer>{}
