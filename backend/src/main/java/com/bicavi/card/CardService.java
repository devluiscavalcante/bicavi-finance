package com.bicavi.card;

import com.bicavi.common.ConflictException;
import com.bicavi.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Mesmas regras de CategoryService: tudo filtrado pelo dono, nome único na
// conta sem diferenciar maiúsculas, renomear para o próprio nome não é conflito.
@Service
public class CardService {

    private final CardRepository repository;

    public CardService(CardRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CardResponse> list(Long userId) {
        return repository.findByUserIdOrderByNameAsc(userId).stream()
                .map(CardResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CardResponse get(Long userId, Long id) {
        return CardResponse.from(findOrThrow(userId, id));
    }

    @Transactional
    public CardResponse create(Long userId, CardRequest request) {
        String name = request.name().trim();
        ensureNameIsFree(userId, name);
        return CardResponse.from(repository.save(new Card(userId, name)));
    }

    @Transactional
    public CardResponse rename(Long userId, Long id, CardRequest request) {
        Card card = findOrThrow(userId, id);
        String newName = request.name().trim();
        if (!newName.equals(card.getName())) {
            // "nubank" -> "Nubank" só muda maiúsculas: não conflita consigo mesmo.
            if (!newName.equalsIgnoreCase(card.getName())) {
                ensureNameIsFree(userId, newName);
            }
            card.rename(newName);
        }
        return CardResponse.from(card);
    }

    // As despesas do cartão ficam sem cartão (ON DELETE SET NULL na V8).
    @Transactional
    public void delete(Long userId, Long id) {
        repository.delete(findOrThrow(userId, id));
    }

    private Card findOrThrow(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Cartão " + id + " não encontrado"));
    }

    private void ensureNameIsFree(Long userId, String name) {
        if (repository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new ConflictException("Já existe um cartão com o nome '" + name + "'");
        }
    }
}
