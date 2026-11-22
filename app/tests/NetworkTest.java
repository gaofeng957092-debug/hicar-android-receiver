package local.hicarlab;
import java.util.*;
import java.nio.file.*;
public class NetworkTest {
 static byte[] hex(String x){if(x.equals("-"))return null;byte[]b=new byte[x.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(x.substring(i*2,i*2+2),16);return b;}
 static void check(boolean x){if(!x)throw new AssertionError();}
 static NearbyWire.Frame capabilities(byte[] fields) {
  return new NearbyWire.Frame(4,0,NearbyWire.tlv(1,NearbyWire.join(NearbyWire.uint(3),NearbyWire.tlv(3,fields))));
 }
 public static void main(String[]args)throws Exception{
  NetworkResource.Offer offer=new NetworkResource.Offer("LAB","02:11:22:33:44:66","not-a-real-password","192.168.49.1","192.168.49.2",5745,4005);
  NetworkResource r=new NetworkResource(offer);r.dataPort=4006;check(r.respond(new NearbyWire.Frame(4,0,new byte[0]))==null);r.authenticated=true;int cases=0;
  for(String line:Files.readAllLines(Paths.get(args[0]))){String[]v=line.split(" ");int kind=Integer.parseInt(v[0]);byte[]expected=hex(v[2]);
   if(kind==-1){List<byte[]>p=r.takePending();check(p.size()==1);check(Arrays.equals(p.get(0),expected));}
   else check(Arrays.equals(r.respond(new NearbyWire.Frame(kind,0,hex(v[1]))),expected));cases++;
  }
  check(r.negotiated&&r.credentialsSent);check(r.respond(new NearbyWire.Frame(4,1,new byte[0]))==null);
  NetworkResource extended=new NetworkResource(offer);extended.authenticated=true;
  byte[] caps=hex("828184818590");
  check(Arrays.equals(extended.respond(capabilities(NearbyWire.join(caps,hex("8a818a828281")))),
    hex("7f848080898287838183830171ac77cf")));
  check(Arrays.equals(extended.respond(capabilities(hex("828184818510000000ff8a0102030485"))),
    hex("7f848080898287838183830171ac77cf")));
  byte[] honor=hex("83818181828183818481868187018189818a01020304858080808080807f83");
  check(Arrays.equals(extended.respond(capabilities(honor)),hex("7f848080898287838183830171ac77cf")));
  byte[] honorShort=hex("838181818281838184818681878189818a01020304858080808080807f83");
  check(Arrays.equals(extended.respond(capabilities(honorShort)),hex("7f848080898287838183830171ac77cf")));
  boolean invalidHonor=false;
  honor[23]=(byte)0x86;
  try{extended.respond(capabilities(honor));}
  catch(NearbyWire.Invalid e){invalidHonor=true;}
  check(invalidHonor);
  boolean conflict=false;
  try{extended.respond(capabilities(NearbyWire.join(caps,hex("8280"))));}
  catch(NearbyWire.Invalid e){conflict=true;}
  check(conflict);
  for(String mac:new String[]{"02:00:00:00:00:00","00:00:00:00:00:00","invalid"}){boolean rejected=false;try{new NetworkResource.Offer("LAB",mac,"not-a-real-password","192.168.49.1","192.168.49.2",5745,4005);}catch(Exception e){rejected=true;}check(rejected);}
  check(new String(NetworkResource.Offer.ssidOnly("LAB","not-a-real-password","192.168.49.1","192.168.49.2",5745,4005).credentials(),java.nio.charset.StandardCharsets.US_ASCII).contains("LAB\n\nnot-a-real-password\n5745"));
  System.out.println(cases+" network negotiation packets match upstream Python; duplicate extensions accepted, conflicts rejected");
 }
}
