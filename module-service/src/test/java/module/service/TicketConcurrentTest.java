package module.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;

import dto.movie.MovieShowingResponse;
import dto.ticket.TicketDTO;
import dto.ticket.TicketStatus;
import module.entity.Movie;
import module.entity.Sales;
import module.entity.Showing;
import module.entity.Ticket;
import module.repository.movie.MovieRepository;
import module.repository.sales.SalesRepository;
import module.repository.showing.ShowingRepository;
import module.repository.ticket.TicketRepository;
import module.service.ticket.TicketService;

@SpringBootTest
public class TicketConcurrentTest {

	@Autowired
	private TicketRepository ticketRepository;
	@Autowired
	private TicketService ticketService;
	@Autowired
	private SalesRepository salesRepository;
	@Autowired
	private ShowingRepository showingRepository;
	@Autowired
	private MovieRepository movieRepository;

	@Test
	public void 티켓조회() {
		List<Ticket> tickets = ticketRepository.findAll();
		assertThat(tickets).isNotNull();
		Ticket build = Ticket.builder().ticketId(tickets.get(0).getTicketId()).build();
		Ticket build1 = Ticket.builder().ticketId(tickets.get(1).getTicketId()).build();
		Ticket build2 = Ticket.builder().ticketId(tickets.get(2).getTicketId()).build();

		List<Ticket> ticketList = List.of(build1,build2,build);
		List<Sales> allByTicketIn = salesRepository.findAllByTicketIn(ticketList);
		assertThat(allByTicketIn.size()).isEqualTo(3);
	}

	@Test
	@DisplayName("[소요시간 테스트] 한 건의 티켓 예매 소요시간 테스트")
	public void limitTimeTest() {
		//given
		long stTime = System.currentTimeMillis();
		System.out.println("시작시간 : " + stTime);
		Long showingId = 8392L;
		Showing showing = Showing.builder()
			.showingId(showingId).build();
		List<TicketDTO> ticketList = ticketRepository.findAllByShowing(showing)
			.stream().map(ticket -> ticket.getTicketId())
			.map(id -> TicketDTO.builder().ticketId(id).build())
			.toList().subList(0,5);

		//when
		ticketService.reserve(showingId, "dbdb1114", ticketList);

		//then
		long edTime = System.currentTimeMillis();
		System.out.println("종료시간 : " + edTime);
		System.out.println("총 소요시간 : " + (edTime - stTime));
	}

	/**
	 * 트래픽이 몰렸을 때 건별 소요시간을 확인해보면 어떨까
	 * AOP에서 락 획득부터 락 해제까지 총 걸리는 시간을 리퀘스트별로 집계한다.
	 *
	 * 1. 별도 스레드 하나 만들어서 시작 끝 로그를 비동기적으로 남기도록
	 * */
	@Test
	@DisplayName("[동시성 테스트] 1000명의 유저 티켓 동시 구매 시도")
	public void concurrentTest() throws InterruptedException {
		// given
		Long showingId = 8392L;
		Showing showing = Showing.builder()
			.showingId(showingId).build();
		List<TicketDTO> ticketList = ticketRepository.findAllByShowing(showing)
			.stream().map(ticket -> ticket.getTicketId())
			.map(id -> TicketDTO.builder().ticketId(id).build())
			.toList();

		// when
		CountDownLatch latch = new CountDownLatch(1000);

		IntStream.range(1, 1001).parallel().forEach(i -> {
			String username = "vuser" + i;
			TicketDTO ticketDTO = ticketList.get(i % ticketList.size());
			List<TicketDTO> ticketDTOList = List.of(ticketDTO);
			Thread.startVirtualThread(() -> {
				try {
					ticketService.reserve(showingId, username, ticketDTOList);
				} finally {
					latch.countDown(); // 스레드 작업 완료 시 감소
				}
			});
		});

		latch.await();

		// then
		// 모든 티켓 reserved 변경
		// 해당 티켓들의 sales 갯수 reserved ticket의 갯수와 동일
		List<Ticket> resultTicketList = ticketRepository.findAllByShowing(showing);
		resultTicketList.forEach(ticket -> System.out.println(ticket.getTicketStatus()));

		boolean isAllReserved = resultTicketList.stream().anyMatch(ticket -> ticket.getTicketStatus() != TicketStatus.RESERVED);
		assertThat(isAllReserved).isFalse();

		List<Sales> salesResultList = salesRepository.findAllByTicketIn(resultTicketList);
		assertThat(salesResultList.size()).isEqualTo(resultTicketList.size());
	}

}
