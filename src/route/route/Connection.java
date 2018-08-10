package route.route;

import java.util.HashSet;
import java.util.Set;

import route.circuit.pin.Pin;
import route.circuit.resource.RouteNode;
import route.hierarchy.LeafNode;

public class Connection implements Comparable<Connection>  {
	public final int id;//Unique ID number
    
	public final Pin source;
	public final Pin sink;

    public Net net;
    public LeafNode leafNode;
    public final int boundingBox;
	
	public final String netName;
	
	public final RouteNode sourceRouteNode;
	public final RouteNode sinkRouteNode;
	
	public final Set<RouteNode> routeNodes;
	
	public boolean isGlobal;
	
	public Connection(int id, Pin source, Pin sink) {
		this.id = id;
		
		this.source = source;
		this.sink = sink;

		//Source Route Node
		String sourceName = null;
		if(this.source.getPortType().isEquivalent()) {
			sourceName = this.source.getPortName();
		}else{
			sourceName = this.source.getPortName() + "[" + this.source.getIndex() + "]";
		}
		this.sourceRouteNode = this.source.getOwner().getSiteInstance().getSource(sourceName);
		
		//Sink route Node
		String sinkName = null;
		if(this.sink.getPortType().isEquivalent()) {
			sinkName = this.sink.getPortName();
		}else{
			sinkName = this.sink.getPortName() + "[" + this.sink.getIndex() + "]";
		}
		this.sinkRouteNode = this.sink.getOwner().getSiteInstance().getSink(sinkName);
		
		//Bounding box
		this.boundingBox = this.calculateBoundingBox();
		
		//Route nodes
		this.routeNodes = new HashSet<RouteNode>();
		
		//Net name
		this.netName = this.source.getNetName();
		
		this.net = null;
		this.leafNode = null;
	}
	private int calculateBoundingBox() {
		int min_x, max_x, min_y, max_y;
		
		int sourceX = this.source.getOwner().getColumn();
		int sinkX = this.sink.getOwner().getColumn();
		if(sourceX < sinkX) {
			min_x = sourceX;
			max_x = sinkX;
		} else {
			min_x = sinkX;
			max_x = sourceX;
		}
		
		int sourceY = this.source.getOwner().getRow();
		int sinkY = this.sink.getOwner().getRow();
		if(sourceY < sinkY) {
			min_y = sourceY;
			max_y = sinkY;
		} else {
			min_y = sinkY;
			max_y = sourceY;
		}
		
		return (max_x - min_x + 1) + (max_y - min_y + 1);
	}
	
	public void setNet(Net net) {
		this.net = net;
	}
	public void setLeafNode() {
		this.leafNode = this.sink.getOwner().getLeafNode();//TODO SINK OR SOURCE?
		this.isGlobal =  (this.source.getOwner().getLeafNode().getIndex() != this.sink.getOwner().getLeafNode().getIndex());
	}
	
	public boolean hasLeafNode() {
		return this.leafNode != null;
	}
	public boolean isGlobal() {
		return this.isGlobal;
	}

	public boolean isInBoundingBoxLimit(RouteNode node) {
		return this.net.isInBoundingBoxLimit(node);
	}
	
	public void addRouteNode(RouteNode routeNode) {
		this.routeNodes.add(routeNode);
	}
	public void resetConnection() {
		this.routeNodes.clear();
	}

	@Override
	public String toString() {
		return this.id + "_" + this.netName;
	}
	
	@Override
	public boolean equals(Object o) {
		if (o == null) return false;
	    if (!(o instanceof Connection)) return false;
	   
	    Connection co = (Connection) o;
		if(this.id == co.id){
			return true;
		} else {
			return false;
		}
	}
	
	@Override
	public int hashCode() {
		return this.id;
	}
	
	@Override
	public int compareTo(Connection other) {
		if(this.id > other.id) {
			return 1;
		} else {
			return -1;
		}
	}
	
	public boolean congested() {
		for(RouteNode rn : this.routeNodes){
			if(rn.overUsed()) {
				return true;
			}
		}
		return false;
	}
	public boolean congested(int thread) {
		for(RouteNode rn : this.routeNodes){
			if(rn.overUsed(thread)) {
				return true;
			}
		}
		return false;
	}
}
