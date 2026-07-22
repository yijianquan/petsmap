package com.wujia.pet.repository;
import com.wujia.pet.entity.DictionaryItem;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DictionaryItemRepository extends JpaRepository<DictionaryItem,Long>{
 List<DictionaryItem> findByDictTypeAndEnabledTrueOrderBySortOrderAscIdAsc(String type);
 List<DictionaryItem> findByDictTypeOrderBySortOrderAscIdAsc(String type);
 Optional<DictionaryItem> findByDictTypeAndItemCode(String type,String code);
}
