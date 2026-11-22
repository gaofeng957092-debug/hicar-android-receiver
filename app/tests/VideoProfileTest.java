package local.hicarlab;

public final class VideoProfileTest {
    static void check(boolean condition){if(!condition)throw new AssertionError();}
    public static void main(String[] args){
        check(VideoProfile.widthFor(1440,3048,2032)==2160);
        check(VideoProfile.widthFor(1080,3048,2032)==1620);
        check(VideoProfile.widthFor(1080,2560,1600)==1728);
        check(VideoProfile.widthFor(1080,1920,1080)==1920);
        check(VideoProfile.widthFor(1080,2340,1080)==2340);
        check(VideoProfile.widthFor(1440,2032,3048)==960);
        check(VideoProfile.widthFor(1080,4096,100)==4096);
        check(VideoProfile.widthFor(480,1,4096)==2);
        int rounded=VideoProfile.widthFor(1080,1921,1080);check(rounded==1922&&(rounded&1)==0);
        check(VideoProfile.bitrateFor(2160,1440)==6220800);
        check(VideoProfile.bitrateFor(1620,1080)==3499200);
        check(VideoProfile.bitrateFor(720,480)==2000000);
        check(VideoProfile.bitrateFor(4096,4096)==12000000);
        boolean rejected=false;try{VideoProfile.widthFor(1080,0,0);}catch(IllegalArgumentException e){rejected=true;}check(rejected);
        System.out.println("PASS: tablet/phone/portrait aspect ratios, even codec dimensions, size limits and resolution-scaled bitrate");
    }
}
