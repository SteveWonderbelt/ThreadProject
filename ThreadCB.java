package osp.Threads;
import java.util.Vector;
import java.util.Enumeration;
import osp.Utilities.*;
import osp.IFLModules.*;
import osp.Tasks.*;
import osp.EventEngine.*;
import osp.Hardware.*;
import osp.Devices.*;
import osp.Memory.*;
import osp.Resources.*;

/**

	Name: Steven Childs
	Email: schilds@email.sc.edu
	
   This class is responsible for actions related to threads, including
   creating, killing, dispatching, resuming, and suspending threads.

   @OSPProject Threads
*/
public class ThreadCB extends IflThreadCB 
{
	static GenericList readyQueue;
    /**
       The thread constructor. Must call 

       	   super();

       as its first statement.

       @OSPProject Threads
    */
    public ThreadCB()
    {
        // your code goes here
		super();
    }

    /**
       This method will be called once at the beginning of the
       simulation. The student can set up static variables here.
       
       @OSPProject Threads
    */
    public static void init()
    {
        // your code goes here
		readyQueue = new GenericList();
    }

    /** 
        Sets up a new thread and adds it to the given task. 
        The method must set the ready status 
        and attempt to add thread to task. If the latter fails 
        because there are already too many threads in this task, 
        so does this method, otherwise, the thread is appended 
        to the ready queue and dispatch() is called.

	The priority of the thread can be set using the getPriority/setPriority
	methods. However, OSP itself doesn't care what the actual value of
	the priority is. These methods are just provided in case priority
	scheduling is required.

	@return thread or null

        @OSPProject Threads
    */
    static public ThreadCB do_create(TaskCB task)
    {
        // your code goes here
		if(task==null){
			dispatch();
			return null;
		}else if(task.getThreadCount()==MaxThreadsPerTask){
			dispatch();
			return null;
		}else{
			
			//create the thread here
			//if the task is not null and we are not at the Max Threads limit, go ahead and creat the thread. 
			ThreadCB ourThread = new ThreadCB();
			ourThread.setPriority(task.getPriority());
			ourThread.setStatus(ThreadReady);
			ourThread.setTask(task);
			if(task.addThread(ourThread)==0){
				dispatch();
				return null; 
			}
			readyQueue.append(ourThread);
			dispatch();
			return ourThread; 
			
		}
    }

    /** 
	Kills the specified thread. 

	The status must be set to ThreadKill, the thread must be
	removed from the task's list of threads and its pending IORBs
	must be purged from all device queues.
        
	If some thread was on the ready queue, it must removed, if the 
	thread was running, the processor becomes idle, and dispatch() 
	must be called to resume a waiting thread.
	
	@OSPProject Threads
    */
    public void do_kill()
    {
        // your code goes here
		//code for ThreadReady status
		if(this.getStatus()==ThreadReady){
			readyQueue.remove(this);
		//code for ThreadRunning status
		}else if(this.getStatus()==ThreadRunning){
			if(MMU.getPTBR().getTask().getCurrentThread()==this){
				MMU.setPTBR(null);
				//getTask().setCurrentThread(null);
			}
		}
		//code for the ThreadWaiting status
		TaskCB tempTask = this.getTask(); //get the current task
		tempTask.removeThread(this); //remove that task from the thread
		this.setStatus(ThreadKill); //set the current thread to ThreadKill
		
		//cycle through list of devices to release resources
		for(int i = 0; i<Device.getTableSize(); i++){
			Device.get(i).cancelPendingIO(this);
		}
		ResourceCB.giveupResources(this);
		dispatch();
		
		//check to see if the task has any more threads and kill them if there are. 
		if(this.getTask().getThreadCount()==0){
			this.getTask().kill();
		}
		
		
		
		
    }

    /** Suspends the thread that is currenly on the processor on the 
        specified event. 

        Note that the thread being suspended doesn't need to be
        running. It can also be waiting for completion of a pagefault
        and be suspended on the IORB that is bringing the page in.
	
	Thread's status must be changed to ThreadWaiting or higher,
        the processor set to idle, the thread must be in the right
        waiting queue, and dispatch() must be called to give CPU
        control to some other thread.

	@param event - event on which to suspend this thread.

        @OSPProject Threads
    */
    public void do_suspend(Event event)
    {
        // check to see if the thread is running
		// if so, check again by checking what the system thinks is the running thread
		// if that checks, set the thread to waiting
		if(this.getStatus()==ThreadRunning){
			if(MMU.getPTBR().getTask().getCurrentThread() == this){
				MMU.setPTBR(null);
				this.getTask().setCurrentThread(null);
				this.setStatus(ThreadWaiting);
			}
		//IF the thread is waiting, just increment its status by one. 
		}else if(this.getStatus()>=ThreadWaiting){
			this.setStatus(this.getStatus()+1);
		}
		//make sure the thread isn't in the ready queue.
		//if it's not, add the event to the thread
		if(!readyQueue.contains(this)){
			event.addThread(this);
		}
		dispatch();
    }

    /** Resumes the thread.
        
	Only a thread with the status ThreadWaiting or higher
	can be resumed.  The status must be set to ThreadReady or
	decremented, respectively.
	A ready thread should be placed on the ready queue.
	
	@OSPProject Threads
    */
    public void do_resume()
    {
        // your code goes here
		if(getStatus() < ThreadWaiting){
			MyOut.print(this,
				"Attempt to resume "
				+this+", which wasn't waiting");
			return; 
		}
		MyOut.print(this, "Resuming "+this);
		
		//set thread status
		if(getStatus() == ThreadWaiting){
			setStatus(ThreadReady);
		}else if(getStatus() > ThreadWaiting){
			setStatus(getStatus()-1);
		}
		
		//Put the thread on onthe ready queue, if appropriate
		if(getStatus() == ThreadReady){
			readyQueue.append(this);
		}
		dispatch();
    }

    /** 
        Selects a thread from the run queue and dispatches it. 

        If there is just one theread ready to run, reschedule the thread 
        currently on the processor.

        In addition to setting the correct thread status it must
        update the PTBR.
	
	@return SUCCESS or FAILURE

        @OSPProject Threads
    */
    public static int do_dispatch()
    {
        // your code goes here
		ThreadCB tempThread = null; //need a thread object to dispatch
		ThreadCB addThread = null; 
		
		try{
			tempThread = MMU.getPTBR().getTask().getCurrentThread(); //need to make sure we don't break the program with an exception
		}catch(NullPointerException e){}
		
		if(tempThread != null){ //check if there is currently running thread
			tempThread.getTask().setCurrentThread(null); //task is informed that its not he current thread
			MMU.setPTBR(null); //take away CPU control
			tempThread.setStatus(ThreadReady); //set the threads status to ThreadReady
			readyQueue.append(tempThread);//add the thread to the ready queue
		}
		
		//Select thread at head of the ready queue
		addThread = (ThreadCB)readyQueue.removeHead();
		
		//if ready queue is empty set the PTBR to null and return failure. 
		if(readyQueue.isEmpty()){
			MMU.setPTBR(null);
			return FAILURE; 
		}else {
			MMU.setPTBR(addThread.getTask().getPageTable());
			addThread.getTask().setCurrentThread(addThread);
			addThread.setStatus(ThreadRunning);
		}
		return SUCCESS;
		
		
		
		
    }

    /**
       Called by OSP after printing an error message. The student can
       insert code here to print various tables and data structures in
       their state just after the error happened.  The body can be
       left empty, if this feature is not used.

       @OSPProject Threads
    */
    public static void atError()
    {
        // your code goes here

    }

    /** Called by OSP after printing a warning message. The student
        can insert code here to print various tables and data
        structures in their state just after the warning happened.
        The body can be left empty, if this feature is not used.
       
        @OSPProject Threads
     */
    public static void atWarning()
    {
        // your code goes here

    }


    /*
       Feel free to add methods/fields to improve the readability of your code
    */

}

/*
      Feel free to add local classes to improve the readability of your code
*/
