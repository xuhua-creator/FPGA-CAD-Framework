package route.circuit.resource;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import route.circuit.Circuit;
import route.circuit.architecture.Architecture;
import route.circuit.architecture.BlockCategory;
import route.circuit.architecture.BlockType;
import route.circuit.resource.Site;
import route.circuit.resource.RouteNode;

public class ResourceGraph {
	private final Circuit circuit;
	private final Architecture architecture;
	
	private final int width, height;
    
	private final List<Site> sites;
	private final Site[][] siteArray;
	
	private final List<RouteNode> routeNodes;
	private List<IndexedData> indexedDataList;
	private List<RouteSwitch> switchTypesList;
	
	private final Map<RouteNodeType, List<RouteNode>> routeNodeMap;
	
	private static int SOURCE_COST_INDEX = 0;
	private static int SINK_COST_INDEX = 1;
	private static int OPIN_COST_INDEX = 2;
	private static int IPIN_COST_INDEX = 3;
	
    public ResourceGraph(Circuit circuit) {
    	this.circuit = circuit;
    	this.architecture = this.circuit.getArchitecture();
    	
    	this.width = this.architecture.getWidth();
    	this.height = this.architecture.getHeight();
    	
		this.sites = new ArrayList<>();
		this.siteArray = new Site[this.width+2][this.height+2];
		
		this.routeNodes = new ArrayList<>();
		this.routeNodeMap = new HashMap<>();
		for(RouteNodeType routeNodeType: RouteNodeType.values()){
			List<RouteNode> temp = new ArrayList<>();
			this.routeNodeMap.put(routeNodeType, temp);
		}
    }
    
    public void build(){
        this.createSites();
        
		try {
			this.generateRRG(this.architecture.getRRGFile().getAbsolutePath());
		} catch (IOException e) {
			System.err.println("Problem in generating RRG: " + e.getMessage());
			e.printStackTrace();
		}
		
		this.assignNamesToSourceAndSink();
		this.connectSourceAndSinkToSite();
		
		//this.testRRG();
		
		//this.printRoutingGraph();
    }
    
    public IndexedData get_ipin_indexed_data() {
    	return this.indexedDataList.get(IPIN_COST_INDEX);
    }
    public IndexedData get_opin_indexed_data() {
    	return this.indexedDataList.get(OPIN_COST_INDEX);
    }
    public IndexedData get_source_indexed_data() {
    	return this.indexedDataList.get(SOURCE_COST_INDEX);
    }
    public IndexedData get_sink_indexed_data() {
    	return this.indexedDataList.get(SINK_COST_INDEX);
    }
    public List<IndexedData> getIndexedDataList() {
    	return this.indexedDataList;
    }
    
    private void createSites() {
        BlockType ioType = BlockType.getBlockTypes(BlockCategory.IO).get(0);
        int ioCapacity = this.architecture.getIoCapacity();
        int ioHeight = ioType.getHeight();
        
        //IO Sites
        for(int i = 1; i < this.height + 1; i++) {
        	this.addSite(new Site(0, i, ioHeight, ioType, ioCapacity));
            this.addSite(new Site(this.width + 1, i, ioHeight, ioType, ioCapacity));
        }
        for(int i = 1; i < this.width + 1; i++) {
        	this.addSite(new Site(i, 0, ioHeight, ioType, ioCapacity));
            this.addSite(new Site(i, this.height + 1, ioHeight, ioType, ioCapacity));
        }
        
        for(int column = 1; column < this.width + 1; column++) {
            BlockType blockType = this.circuit.getColumnType(column);
            int blockHeight = blockType.getHeight();
            for(int row = 1; row < this.height + 2 - blockHeight; row += blockHeight) {
            	this.addSite(new Site(column, row, blockHeight, blockType, 1));
            }
        }
    }
    public void addSite(Site site) {
    	this.siteArray[site.getColumn()][site.getRow()] = site;
    	this.sites.add(site);
    }
    
    /**
     * Return the site at coordinate (x, y). If allowNull is false,
     * return the site that overlaps coordinate (x, y) but possibly
     * doesn't start at that position.
     */
    public Site getSite(int column, int row) {
        return this.getSite(column, row, false);
    }
    public Site getSite(int column, int row, boolean allowNull) {
        if(allowNull) {
            return this.siteArray[column][row];
        } else {
            Site site = null;
            int topY = row;
            while(site == null) {
                site = this.siteArray[column][topY];
                topY--;
            }
            
            return site;
        }
    }
    public List<Site> getSites(BlockType blockType) {
        BlockType ioType = BlockType.getBlockTypes(BlockCategory.IO).get(0);
        List<Site> sites;
        
        if(blockType.equals(ioType)) {
            int ioCapacity = this.architecture.getIoCapacity();
            sites = new ArrayList<Site>((this.width + this.height) * 2 * ioCapacity);
            
            for(int n = 0; n < ioCapacity; n++) {
                for(int i = 1; i < this.height + 1; i++) {
                    sites.add(this.siteArray[0][i]);
                    sites.add(this.siteArray[this.width + 1][i]);
                }
                
                for(int i = 1; i < this.width + 1; i++) {
                    sites.add(this.siteArray[i][0]);
                    sites.add(this.siteArray[i][this.height + 1]);
                }
            }
        } else {
            List<Integer> columns = this.circuit.getColumnsPerBlockType(blockType);
            int blockHeight = blockType.getHeight();
            sites = new ArrayList<Site>(columns.size() * this.height);
            
            for(Integer column : columns) {
                for(int row = 1; row < this.height + 2 - blockHeight; row += blockHeight) {
                    sites.add(this.siteArray[column][row]);
                }
            }
        }
    
        return sites;
    }
    public List<Site> getSites(){
    	return this.sites;
    }
    
    /******************************
     * GENERATE THE RRG READ FROM * 
     * RRG FILE DUMPED BY VPR     *
     ******************************/
    
	private void generateRRG(String rrgFileName) throws IOException {
		System.out.println("---------------");
		System.out.println("| Process RRG |");
		System.out.println("---------------");
		
		BufferedReader reader = null;
		String line = null;
		String[] words = null;
		
		/*****************************
		 *        Indexed Data       *
		 *****************************/
		
		this.indexedDataList = new ArrayList<>();
		
		reader = new BufferedReader(new FileReader(rrgFileName.replace("rr_graph", "rr_indexed_data")));
		System.out.println("   Read " + rrgFileName.split("/")[rrgFileName.split("/").length - 1].replace("rr_graph", "rr_indexed_data"));
		
		while ((line = reader.readLine()) != null) {
			
			line = line.trim();
			if (line.length() > 0) {
				
				this.indexedDataList.add(new IndexedData(line));
			}
		}
        reader.close();
        
        for (IndexedData data : this.indexedDataList) {
        	if (data.orthoCostIndex != -1) {
        		data.setOrthoData(this.indexedDataList.get(data.orthoCostIndex));
        	}
        }
        
        //for(IndexedData data : indexedDataList) {
        //	System.out.println(data);
        //}
        
		/*****************************
		 *        Switch Types       *
		 *****************************/
		
		this.switchTypesList = new ArrayList<>();
		
		reader = new BufferedReader(new FileReader(rrgFileName.replace("rr_graph", "rr_switch_types")));
		System.out.println("   Read " + rrgFileName.split("/")[rrgFileName.split("/").length - 1].replace("rr_graph", "rr_switch_types"));
		
		while ((line = reader.readLine()) != null) {
			
			line = line.trim();
			if (line.length() > 0) {
				
				this.switchTypesList.add(new RouteSwitch(line));
			}
		}
		
        reader.close();
        
		//for(SwitchType type : switchTypesList) {
		//	System.out.println(type);
		//}
		
		/*****************************
		 *        Route Nodes        *
		 *****************************/
		
		RouteNode routeNode = null;
		String currentPort = null;
		int portIndex = -1;
		IndexedData data = null;
		
		reader = new BufferedReader(new FileReader(rrgFileName.replace("rr_graph", "rr_nodes")));
		System.out.println("   Read " + rrgFileName.split("/")[rrgFileName.split("/").length - 1].replace("rr_graph", "rr_nodes"));
		
		while ((line = reader.readLine()) != null) {
        	
			line = line.trim();
			if(line.length() > 0){
        		
        		words = line.split(";");
        		
        		int index = Integer.parseInt(words[0]);
        		String type = words[1];
        		String name = words[2];
        		int xlow = Integer.parseInt(words[3]);
        		int xhigh = Integer.parseInt(words[4]);
        		int ylow = Integer.parseInt(words[5]);
        		int yhigh = Integer.parseInt(words[6]);
        		int n = Integer.parseInt(words[7]);
        		
        		if(n == 0){//New global block, reset data
        			currentPort = null;
        			portIndex = -1;
        		}
        		
        		int cap = Integer.parseInt(words[8]);
        		float r = Float.parseFloat(words[9]);
        		float c = Float.parseFloat(words[10]);
        		
        		int cost_index = Integer.parseInt(words[11]);
        		data = this.indexedDataList.get(cost_index);
        		
        		int numChildren = Integer.parseInt(words[12]);
        		
        		switch (type) {
        			case "SOURCE":        				
        				//Assertions
        				assert name.equals("-");
        				assert r == 0;
        				assert c == 0;
        				
        				routeNode = new Source(index, xlow, xhigh, ylow, yhigh, n, cap, data, numChildren);
        				
        				break;
        			case "SINK":        				
        				//Assertions
        				assert name.equals("-");
        				assert r == 0;
        				assert c == 0;
        				
        				routeNode = new Sink(index, xlow, xhigh, ylow, yhigh, n, cap, data, numChildren);
        				
        				break;
        			case "IPIN":
        				//Assertions
        				assert cap == 1;
        				assert r == 0;
        				assert c == 0;
        				
        				if(currentPort == null){
        					currentPort = name;
        					portIndex = 0;
        				}else if(!currentPort.equals(name)){
        					currentPort = name;
        					portIndex = 0;
        				}
        				
        				routeNode = new Ipin(index, xlow, xhigh, ylow, yhigh, n, name, portIndex, data, numChildren);
        				
        				portIndex += 1;
        				
        				break;
        			case "OPIN":        				
        				//Assertions
        				assert cap == 1;
        				assert r == 0;
        				assert c == 0;
        				
        				if(currentPort == null){
        					currentPort = name;
        					portIndex = 0;
        				}else if(!currentPort.equals(name)){
        					currentPort = name;
        					portIndex = 0;
        				}
        				
        				routeNode = new Opin(index, xlow, xhigh, ylow, yhigh, n, name, portIndex, data, numChildren);
        				
        				portIndex += 1;
        				
        				break;
        			case "CHANX":        				
        				//Assertions
        				assert name.equals("-");
        				assert cap == 1;
        				
        				routeNode = new Chanx(index, xlow, xhigh, ylow, yhigh, n, r, c, data, numChildren);
        				
        				break;
        			case "CHANY":        				
        				//Assertions
        				assert name.equals("-");
        				assert cap == 1;
        				
        				routeNode = new Chany(index, xlow, xhigh, ylow, yhigh, n, r, c, data, numChildren);
        				
        				break;
        			default:
        				System.out.println("Unknown type: " + type);
        				break;
        		}
        		this.addRouteNode(routeNode);
        	}
		}
		
		reader.close();
		
		/*****************************
		 *         Children          *
		 *****************************/
		
		reader = new BufferedReader(new FileReader(rrgFileName.replace("rr_graph", "rr_children")));
		System.out.println("   Read " + rrgFileName.split("/")[rrgFileName.split("/").length - 1].replace("rr_graph", "rr_children"));
		
		while ((line = reader.readLine()) != null) {
			
			line = line.trim();
        	if(line.length() > 0){
        		
        		words = line.split(";");
        		
        		RouteNode parent = this.routeNodes.get(Integer.parseInt(words[0]));
        		
        		int numChildren = Integer.parseInt(words[1]);
        		
        		if(numChildren != parent.numChildren) System.out.println("Problem in resource graph for num children");//TODO remove checker
        		
        		for(int index = 0; index < numChildren; index++) {
        			RouteNode child = this.routeNodes.get(Integer.parseInt(words[index+2]));
        			parent.setChild(index, child);
        		}
        	}
		}
		
		reader.close();
		
		/*****************************
		 *         Switches          *
		 *****************************/
		
		reader = new BufferedReader(new FileReader(rrgFileName.replace("rr_graph", "rr_switches")));
		System.out.println("   Read " + rrgFileName.split("/")[rrgFileName.split("/").length - 1].replace("rr_graph", "rr_switches"));
		
		while ((line = reader.readLine()) != null) {
		
			line = line.trim();
			if(line.length() > 0){
				
				words = line.split(";");
				
				RouteNode parent = this.routeNodes.get(Integer.parseInt(words[0]));
				int numChildren = parent.numChildren;
				
				for(int index = 0; index < numChildren; index++) {
					RouteSwitch routeSwitch = this.switchTypesList.get(Integer.parseInt(words[index+1]));
					parent.setSwitchType(index, routeSwitch);
				}
			}
		}
		
		reader.close();
		
		for(RouteNode node : this.routeNodes) {
			for(int i = 0; i < node.numChildren; i++) {
				RouteNode child = node.children[i];
				RouteSwitch routeSwitch = node.switches[i];
				
				child.setDrivingRouteSwitch(routeSwitch);
			}
		}
		for(RouteNode node : this.routeNodeMap.get(RouteNodeType.SOURCE)) {
			Source source = (Source) node;
			source.setDrivingRouteSwitch(null);//TODO Driving route switch of source?
		}
		
		System.out.println();
	}
	private void assignNamesToSourceAndSink() {
		for(RouteNode routeNode:this.routeNodeMap.get(RouteNodeType.SOURCE)){
			Source source = (Source) routeNode;
			source.setName();
		}
		
		for(RouteNode routeNode:this.routeNodeMap.get(RouteNodeType.IPIN)){
			Ipin ipin = (Ipin) routeNode;
			ipin.setSinkName();
		}
	}
    private void connectSourceAndSinkToSite() {
    	for(RouteNode routeNode:this.routeNodeMap.get(RouteNodeType.SOURCE)){
			Source source = (Source) routeNode;
			
			Site site = this.getSite(source.xlow, source.ylow);
			if(site.addSource((Source)routeNode) == false) {
				System.err.println("Unable to add " + routeNode + " as source to " + site);
			}
		}
    	for(RouteNode routeNode:this.routeNodeMap.get(RouteNodeType.SINK)){
			Sink sink = (Sink) routeNode;
			
			Site site = this.getSite(sink.xlow, sink.ylow);
			if(site.addSink((Sink)routeNode) == false) {
				System.err.println("Unable to add " + routeNode + " as sink to " + site);
			}
		}
    }
	
	private void addRouteNode(RouteNode routeNode) {
		assert routeNode.index == this.routeNodes.size();
		
		this.routeNodes.add(routeNode);
		this.routeNodeMap.get(routeNode.type).add(routeNode);
	}
	public List<RouteNode> getRouteNodes() {
		return this.routeNodes;
	}
	public int numRouteNodes() {
		return this.routeNodes.size();
	}
	public int numRouteNodes(RouteNodeType type) {
		if(this.routeNodeMap.containsKey(type)) {
			return this.routeNodeMap.get(type).size();
		} else {
			return 0;
		}
	}
	
	public int[] get_expected_segs_to_target(RouteNode node, RouteNode target) {
		/* Returns the number of segments the same type as inode that will be needed *
		 * to reach target_node (not including inode) in each direction (the same    *
		 * direction (horizontal or vertical) as inode and the orthogonal direction).*/
		RouteNodeType type = node.type;
		
		short ylow, yhigh, xlow, xhigh;
		int num_segs_same_dir, num_segs_ortho_dir;
		
		int no_need_to_pass_by_clb;
		float inv_length, ortho_inv_length;
		
		short target_x = target.xlow;
		short target_y = target.ylow;
		
		IndexedData indexedData = node.indexedData;
		IndexedData orthoIndexedData = indexedData.getOrthoData();
		
		inv_length = indexedData.inv_length;
		ortho_inv_length = orthoIndexedData.inv_length;
		
		if (type == RouteNodeType.CHANX) {
			ylow = node.ylow;
			xhigh = node.xhigh;
			xlow = node.xlow;

			/* Count vertical (orthogonal to inode) segs first. */

			if (ylow > target_y) { /* Coming from a row above target? */
				num_segs_ortho_dir = (int)(Math.ceil((ylow - target_y + 1.) * ortho_inv_length));
				no_need_to_pass_by_clb = 1;
			} else if (ylow < target_y - 1) { /* Below the CLB bottom? */
				num_segs_ortho_dir= (int)(Math.ceil((target_y - ylow) * ortho_inv_length));
				no_need_to_pass_by_clb = 1;
			} else { /* In a row that passes by target CLB */
				num_segs_ortho_dir = 0;
				no_need_to_pass_by_clb = 0;
			}

			/* Now count horizontal (same dir. as inode) segs. */

			if (xlow > target_x + no_need_to_pass_by_clb) {
				num_segs_same_dir = (int)(Math.ceil((xlow - no_need_to_pass_by_clb - target_x) * inv_length));
			} else if (xhigh < target_x - no_need_to_pass_by_clb) {
				num_segs_same_dir = (int)(Math.ceil((target_x - no_need_to_pass_by_clb - xhigh) * inv_length));
			} else {
				num_segs_same_dir = 0;
			}
			
			int[] result = {num_segs_same_dir, num_segs_ortho_dir};
			return result;
		} else { /* inode is a CHANY */
			ylow = node.ylow;
			yhigh = node.yhigh;
			xlow = node.xlow;

			/* Count horizontal (orthogonal to inode) segs first. */

			if (xlow > target_x) { /* Coming from a column right of target? */
				num_segs_ortho_dir = (int)(Math.ceil((xlow - target_x + 1.) * ortho_inv_length));
				no_need_to_pass_by_clb = 1;
			} else if (xlow < target_x - 1) { /* Left of and not adjacent to the CLB? */
				num_segs_ortho_dir = (int)(Math.ceil((target_x - xlow) * ortho_inv_length));
				no_need_to_pass_by_clb = 1;
			} else { /* In a column that passes by target CLB */
				num_segs_ortho_dir = 0;
				no_need_to_pass_by_clb = 0;
			}

			/* Now count vertical (same dir. as inode) segs. */

			if (ylow > target_y + no_need_to_pass_by_clb) {
				num_segs_same_dir = (int)(Math.ceil((ylow - no_need_to_pass_by_clb - target_y) * inv_length));
			} else if (yhigh < target_y - no_need_to_pass_by_clb) {
				num_segs_same_dir = (int)(Math.ceil((target_y - no_need_to_pass_by_clb - yhigh) * inv_length));
			} else {
				num_segs_same_dir = 0;
			}
			
			int[] result = {num_segs_same_dir, num_segs_ortho_dir};
			return result;
		}
	}
	public int get_expected_distance_to_target(RouteNode node, RouteNode target) {
		/* Returns the number of segments the same type as inode that will be needed *
		 * to reach target_node (not including inode) in each direction (the same    *
		 * direction (horizontal or vertical) as inode and the orthogonal direction).*/
		RouteNodeType type = node.type;
		
		short ylow, yhigh, xlow, xhigh;
		int distance_same_dir, distance_ortho_dir;
		
		int no_need_to_pass_by_clb;
		
		short target_x = target.xlow;
		short target_y = target.ylow;
		
		if (type == RouteNodeType.CHANX) {
			ylow = node.ylow;
			xhigh = node.xhigh;
			xlow = node.xlow;

			/* Count vertical (orthogonal to inode) segs first. */

			if (ylow > target_y) { /* Coming from a row above target? */
				distance_ortho_dir = ylow - target_y + 1;
				no_need_to_pass_by_clb = 1;
			} else if (ylow < target_y - 1) { /* Below the CLB bottom? */
				distance_ortho_dir= target_y - ylow;
				no_need_to_pass_by_clb = 1;
			} else { /* In a row that passes by target CLB */
				distance_ortho_dir = 0;
				no_need_to_pass_by_clb = 0;
			}

			/* Now count horizontal (same dir. as inode) segs. */

			if (xlow > target_x + no_need_to_pass_by_clb) {
				distance_same_dir = xlow - no_need_to_pass_by_clb - target_x;
			} else if (xhigh < target_x - no_need_to_pass_by_clb) {
				distance_same_dir = target_x - no_need_to_pass_by_clb - xhigh;
			} else {
				distance_same_dir = 0;
			}
			
			return distance_same_dir + distance_ortho_dir;
		} else { /* inode is a CHANY */
			ylow = node.ylow;
			yhigh = node.yhigh;
			xlow = node.xlow;

			/* Count horizontal (orthogonal to inode) segs first. */

			if (xlow > target_x) { /* Coming from a column right of target? */
				distance_ortho_dir = xlow - target_x + 1;
				no_need_to_pass_by_clb = 1;
			} else if (xlow < target_x - 1) { /* Left of and not adjacent to the CLB? */
				distance_ortho_dir = target_x - xlow;
				no_need_to_pass_by_clb = 1;
			} else { /* In a column that passes by target CLB */
				distance_ortho_dir = 0;
				no_need_to_pass_by_clb = 0;
			}

			/* Now count vertical (same dir. as inode) segs. */

			if (ylow > target_y + no_need_to_pass_by_clb) {
				distance_same_dir = ylow - no_need_to_pass_by_clb - target_y;
			} else if (yhigh < target_y - no_need_to_pass_by_clb) {
				distance_same_dir = target_y - no_need_to_pass_by_clb - yhigh;
			} else {
				distance_same_dir = 0;
			}
			
			return distance_same_dir + distance_ortho_dir;
		}
	}
	
	@Override
	public String toString() {
		String s = new String();
		
		s+= "The system has " + this.numRouteNodes() + " rr nodes:\n";
		
		for(RouteNodeType type : RouteNodeType.values()) {
			s += "\t" + type + "\t" + this.numRouteNodes(type) + "\n";
		}
		return s;
	}
	
	/********************
	 * Routing statistics
	 ********************/
	public int totalWireLength() {
		int totalWireLength = 0;
		for(RouteNode routeNode : this.routeNodes) {
			if(routeNode.isWire) {
				if(routeNode.used()) {
					totalWireLength += routeNode.wireLength();
				}
			}
		}
		return totalWireLength;
	}
	public int congestedTotalWireLengt() {
		int totalWireLength = 0;
		for(RouteNode routeNode : this.routeNodes) {
			if(routeNode.isWire) {
				if(routeNode.used()) {
					totalWireLength += routeNode.wireLength() * routeNode.routeNodeData.occupation;
				}
			}
		}
		return totalWireLength;
	}
	public int wireSegmentsUsed() {
		int wireSegmentsUsed = 0;
		for(RouteNode routeNode : this.routeNodes) {
			if(routeNode.isWire) {
				if(routeNode.used()) {
					wireSegmentsUsed++;
				}
			}
		}
		return wireSegmentsUsed;
	}
	public void sanityCheck() {
		for(Site site:this.getSites()) {
			site.sanityCheck();
		}
	}
	public void printRoutingGraph() {
		for(RouteNode node : this.getRouteNodes()) {
			System.out.println(node);
			for (RouteNode child : node.children) {
				System.out.println("\t" + child);
			}
			System.out.println();
		}
	}
}
