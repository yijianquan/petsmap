package com.wujia.pet.config;

import com.wujia.pet.entity.UserAccount;
import com.wujia.pet.repository.UserAccountRepository;
import com.wujia.pet.entity.DictionaryItem;
import com.wujia.pet.repository.DictionaryItemRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final DictionaryItemRepository dictionaryItemRepository;

    @Value("${app.bootstrap.enabled:true}")
    private boolean bootstrapEnabled;

    @Value("${app.bootstrap.admin-username:admin}")
    private String adminUsername;

    @Value("${app.bootstrap.admin-password:admin123}")
    private String adminPassword;

    public DataInitializer(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            DictionaryItemRepository dictionaryItemRepository) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.dictionaryItemRepository = dictionaryItemRepository;
    }

    @Override
    public void run(String... args) {
        seedDictionaries();
        if (!bootstrapEnabled) {
            return;
        }

        UserAccount account = userAccountRepository.findByUsername(adminUsername)
                .orElseGet(() -> {
                    UserAccount created = new UserAccount();
                    created.setUsername(adminUsername);
                    return created;
                });
        boolean changed = account.getId() == null;
        if (!"ROLE_ADMIN".equals(account.getRole())) {
            account.setRole("ROLE_ADMIN");
            changed = true;
        }
        if (account.getPassword() == null || !passwordEncoder.matches(adminPassword, account.getPassword())) {
            account.setPassword(passwordEncoder.encode(adminPassword));
            changed = true;
        }
        if (changed) {
            userAccountRepository.save(account);
        }
    }

    private void seedDictionaries() {
        seed("PET_GENDER", "MALE", "男孩", null, 10); seed("PET_GENDER", "FEMALE", "女孩", null, 20);
        seed("PET_BREED", "CAT_BRITISH_SHORTHAIR", "英国短毛猫", "CAT", 10); seed("PET_BREED", "CAT_AMERICAN_SHORTHAIR", "美国短毛猫", "CAT", 20); seed("PET_BREED", "CAT_RAGDOLL", "布偶猫", "CAT", 30); seed("PET_BREED", "CAT_SIAMESE", "暹罗猫", "CAT", 40); seed("PET_BREED", "CAT_ORANGE", "中华田园猫", "CAT", 50); seed("PET_BREED", "CAT_OTHER", "其他猫咪", "CAT", 999);
        seed("PET_BREED", "DOG_POODLE", "贵宾犬", "DOG", 10); seed("PET_BREED", "DOG_BICHON", "比熊犬", "DOG", 20); seed("PET_BREED", "DOG_CORGI", "柯基犬", "DOG", 30); seed("PET_BREED", "DOG_GOLDEN", "金毛寻回犬", "DOG", 40); seed("PET_BREED", "DOG_LABRADOR", "拉布拉多犬", "DOG", 50); seed("PET_BREED", "DOG_HUSKY", "哈士奇", "DOG", 60); seed("PET_BREED", "DOG_SHIBA", "柴犬", "DOG", 70); seed("PET_BREED", "DOG_CHINESE", "中华田园犬", "DOG", 80); seed("PET_BREED", "DOG_OTHER", "其他狗狗", "DOG", 999);
        seed("PET_BREED", "OTHER_RABBIT", "兔子", "OTHER", 10); seed("PET_BREED", "OTHER_HAMSTER", "仓鼠", "OTHER", 20); seed("PET_BREED", "OTHER_GUINEA_PIG", "豚鼠", "OTHER", 30); seed("PET_BREED", "OTHER_BIRD", "鸟类", "OTHER", 40); seed("PET_BREED", "OTHER_REPTILE", "爬宠", "OTHER", 50); seed("PET_BREED", "OTHER_OTHER", "其他异宠", "OTHER", 999);
    }
    private void seed(String type,String code,String label,String parent,int sort){if(dictionaryItemRepository.findByDictTypeAndItemCode(type,code).isPresent())return;DictionaryItem item=new DictionaryItem();item.setDictType(type);item.setItemCode(code);item.setLabel(label);item.setParentCode(parent);item.setSortOrder(sort);item.setEnabled(true);dictionaryItemRepository.save(item);}
}
