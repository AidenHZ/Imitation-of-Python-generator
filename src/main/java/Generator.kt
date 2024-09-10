import java.lang.IllegalStateException
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.createCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
class Generator{

}
/*
这是一个kotlin的协程模仿python的generator协程。
目标实现效果;
fun main() {
    val nums = generator { start: Int ->
        for (i in 0..5) {
            yield(start + i)
        }
    }

    val seq = nums(10)

    for (j in seq) {
        println(j)
    }

}
 */
fun<T> generator(block:suspend  GeneratorScope<T>.(T) ->Unit):(T) ->GeneratorA<T>{
    return {
        parameter ->
        GeneratorImpl(block,parameter)
    }

}
//因为要遍历所以返回一个Iterator<T>
interface GeneratorA<T>{
    operator fun iterator():Iterator<T>
}
class GeneratorImpl<T>(private val block: suspend GeneratorScope<T>.(T) -> Unit,private val parameter:T):GeneratorA<T>{
    override fun iterator(): Iterator<T> {
        return GeneratorIterator(block,parameter)
    }
}
abstract class GeneratorScope<T> internal constructor(){
    protected abstract val parameter:T
    abstract suspend fun yield(value: T)
}
class   GeneratorIterator<T>(private val block: suspend GeneratorScope<T>.(T) -> Unit,override val parameter: T)
    : GeneratorScope<T>(),Iterator<T>,Continuation<Any?>{
    override val context: CoroutineContext=EmptyCoroutineContext
    private var state :State
    init {

        val coroutineBlock:suspend GeneratorScope<T>.()-> Unit={block(parameter)}
        //第一个this指向 GeneratorIterator<T>，使得可以正常访问上下文，比如yield
        //第二个this指向是 Continuation<Any?> 的实现，也就是当前的 GeneratorIterator。
        val start = coroutineBlock.createCoroutine(this,this)
        state = State.NotReady(start)
    }

    override suspend fun yield(value: T)= suspendCoroutine {
        continuation ->
        state= when(state){
            is State.NotReady -> State.Ready(continuation,value)
            //使用星投影，不然拿不到T类型
            is State.Ready<*> -> throw IllegalStateException("Cannot yield a value while ready.")
            is State.Done -> throw IllegalStateException("Cannot yield a value while done.")
        }

    }
    private fun resume(){
        when(val currentS = state){
            is State.NotReady -> currentS.continuation.resume(Unit)
            else -> {}
        }
    }

    override fun hasNext(): Boolean {
        //给到状态为Ready，检查是否真的没有元素了。
        resume()
        return state != State.Done
    }

    override fun next(): T {
        return when (val  currentState= state){
            is State.NotReady->{
                resume()
                return next()
            }
            is State.Ready<*> ->{
                state = State.NotReady(currentState.continuation)
                (currentState as State.Ready<T>).value
            }
            State.Done ->throw IndexOutOfBoundsException("No value left.")

        }
    }
    //resumeWith 是协程的回调方法，当协程执行完成或遇到异常时会调用该方法。
    //result 是一个 Result<Any?> 类型的对象，它封装了协程的结果。
    override fun resumeWith(result: Result<Any?>) {
        state = State.Done   //所以每次回调的时候都需要完成
        result.getOrThrow()   //如果 Result 表示一个成功的结果，getOrThrow() 返回该结果。
        //如果 Result 表示一个失败的结果（即封装了一个异常），getOrThrow() 会抛出这个异常。
    }
}
sealed class State{
    class NotReady(val continuation:Continuation<Unit>):State()
    class Ready<T>(val continuation: Continuation<Unit>, val value: T):State()
    object Done:State()
}



fun main() {
    val nums = generator { start: Int ->
        for (i in 0..5) {
            yield(start + i)
        }
    }

    val seq = nums(10)

    for (j in seq) {
        println(j)
    }
}