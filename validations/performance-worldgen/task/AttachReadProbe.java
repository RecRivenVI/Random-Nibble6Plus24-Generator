import com.sun.tools.attach.VirtualMachine;
class AttachReadProbe {
    public static void main(String[] args)throws Exception{
        VirtualMachine vm=VirtualMachine.attach(args[0]);
        try{vm.loadAgent(args[1],args[2]);}finally{vm.detach();}
    }
}
