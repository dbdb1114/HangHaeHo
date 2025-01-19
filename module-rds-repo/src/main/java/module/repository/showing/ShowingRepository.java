package module.repository.showing;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import module.entity.Screen;
import module.entity.Showing;

public interface ShowingRepository extends JpaRepository<Showing, Long>, ShowingCustomRepository {

	@Query("SELECT s FROM Showing s " +
		"WHERE FUNCTION('DATE', s.stTime) = FUNCTION('DATE', :day) " +
		"AND s.screen = :screen " +
		"ORDER BY s.stTime DESC")
	List<Showing> findShowingsByStTimeLikeAndScreenIs(LocalDateTime day, Screen screen);

}