package ca.bc.gov.nrs.cbr.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import ca.bc.gov.nrs.cbr.service.v1.RoadNameService;
import ca.bc.gov.nrs.cbr.struct.v1.RoadSectionKey;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * A mocked {@link RoadNameService} answering from names a test gives it, standing in for LRMOPS.
 *
 * <pre>{@code
 * roads = RoadNamesStub.stub(roadNames);
 * roads.name("R00123", "01", "Bowron FSR");
 * }</pre>
 *
 * <p>Keys are matched as the service matches them ({@link RoadSectionKey#of}). No Forest Service
 * Road filter unless a test stubs {@code sectionsNamed} itself.
 */
public final class RoadNamesStub {

  private final Map<RoadSectionKey, String> names = new HashMap<>();

  private RoadNamesStub() {}

  /** Stubs every name lookup on {@code roadNames} to answer from the names given to the stub. */
  @SuppressWarnings("unchecked")
  public static RoadNamesStub stub(RoadNameService roadNames) {
    RoadNamesStub stub = new RoadNamesStub();
    lenient().when(roadNames.sectionsNamed(any())).thenReturn(Optional.empty());
    lenient().when(roadNames.namesOf(any())).thenAnswer(call -> {
      Map<RoadSectionKey, String> found = new HashMap<>();
      for (Object key : (Collection<Object>) call.getArgument(0)) {
        if (key != null && stub.names.containsKey(key)) {
          found.put((RoadSectionKey) key, stub.names.get(key));
        }
      }
      return found;
    });
    lenient().when(roadNames.nameOf(anyString(), anyString())).thenAnswer(call ->
        stub.names.get(RoadSectionKey.of(call.getArgument(0), call.getArgument(1))));
    lenient().when(roadNames.namesFor(any(), any(), any())).thenAnswer(call -> {
      Function<Object, String> forestFileId = call.getArgument(1);
      Function<Object, String> roadSectionId = call.getArgument(2);
      return (Function<Object, String>) row -> {
        RoadSectionKey key = RoadSectionKey.of(forestFileId.apply(row), roadSectionId.apply(row));
        return key == null ? null : stub.names.get(key);
      };
    });
    return stub;
  }

  /** LRMOPS names this road section so. */
  public RoadNamesStub name(String forestFileId, String roadSectionId, String roadName) {
    names.put(RoadSectionKey.of(forestFileId, roadSectionId), roadName);
    return this;
  }
}
